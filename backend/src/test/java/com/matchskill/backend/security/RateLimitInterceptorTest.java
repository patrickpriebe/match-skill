package com.matchskill.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.matchskill.backend.config.RateLimitProperties;
import com.matchskill.backend.controller.AuthController;
import com.matchskill.backend.controller.ExchangeController;
import com.matchskill.backend.dto.auth.LoginRequest;
import com.matchskill.backend.dto.exchange.CreateExchangeRequest;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.service.RateLimiterService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimiterService rateLimiterService;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRemoteAddr("192.168.1.50");
        response = new MockHttpServletResponse();
        interceptor = new RateLimitInterceptor(rateLimiterService,
                new RateLimitProperties(5, 60, 5, 90, 20, 120));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBlockLoginAndSendRetryAfter() {
        request.setServletPath("/auth/login");

        assertRateLimited(new Object(), 60);
        verify(rateLimiterService).tryConsume("ratelimit:login:192.168.1.50", 5, 60);
    }

    @Test
    void shouldBlockRegisterAndUseItsOwnWindow() {
        request.setServletPath("/auth/register");

        assertRateLimited(new Object(), 90);
        verify(rateLimiterService).tryConsume("ratelimit:register:192.168.1.50", 5, 90);
    }

    @Test
    void shouldUseAuthenticatedUserForExchangeCreation() throws Exception {
        UUID userId = authenticate();
        request.setServletPath("/exchanges;ignored=value");

        assertRateLimited(exchangeCreation(), 120);
        verify(rateLimiterService).tryConsume("ratelimit:create-exchange:" + userId, 20, 120);
    }

    @Test
    void shouldKeepUserBudgetAcrossClientAddresses() throws Exception {
        UUID userId = authenticate();
        String key = "ratelimit:create-exchange:" + userId;
        when(rateLimiterService.tryConsume(key, 20, 120)).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, exchangeCreation())).isTrue();
        request.setRemoteAddr("203.0.113.195");
        assertThat(interceptor.preHandle(request, response, exchangeCreation())).isTrue();

        verify(rateLimiterService, times(2)).tryConsume(key, 20, 120);
    }

    @Test
    void shouldIgnoreUntrustedForwardedAddress() throws Exception {
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        request.setServletPath("/auth/login;ignored=value");
        when(rateLimiterService.tryConsume("ratelimit:login:192.168.1.50", 5, 60)).thenReturn(true);
        HandlerMethod login = new HandlerMethod(mock(AuthController.class), "login", LoginRequest.class);

        assertThat(interceptor.preHandle(request, response, login)).isTrue();
        verify(rateLimiterService).tryConsume("ratelimit:login:192.168.1.50", 5, 60);
    }

    @Test
    void shouldPropagateProtectionOutageWithoutTreatingItAsQuotaExhaustion() {
        request.setServletPath("/auth/login");
        ApiException unavailable = new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "RATE_LIMIT_UNAVAILABLE", "Request protection is temporarily unavailable, try again later");
        when(rateLimiterService.tryConsume("ratelimit:login:192.168.1.50", 5, 60))
                .thenThrow(unavailable);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isSameAs(unavailable);
        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    void shouldIgnoreUnrestrictedRoutes() {
        request.setMethod("GET");
        request.setServletPath("/matches");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void shouldNotThrottleOtherExchangeMethods() throws Exception {
        HandlerMethod getExchange = new HandlerMethod(mock(ExchangeController.class),
                "get", Authentication.class, UUID.class);

        assertThat(interceptor.preHandle(request, response, getExchange)).isTrue();
        verifyNoInteractions(rateLimiterService);
    }

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        return userId;
    }

    private HandlerMethod exchangeCreation() throws NoSuchMethodException {
        return new HandlerMethod(mock(ExchangeController.class), "create",
                Authentication.class, CreateExchangeRequest.class);
    }

    private void assertRateLimited(Object handler, int retryAfter) {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(exception.getMessage()).isEqualTo("Too many requests, try again later");
                });
        assertThat(response.getHeader("Retry-After")).isEqualTo(Integer.toString(retryAfter));
    }
}
