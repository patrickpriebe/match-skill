package com.matchskill.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchskill.backend.config.CorsProperties;
import com.matchskill.backend.config.JwtProperties;
import com.matchskill.backend.config.OAuth2Properties;
import com.matchskill.backend.config.RateLimitProperties;
import com.matchskill.backend.config.SecurityConfig;
import com.matchskill.backend.controller.AuthController;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.service.AuthService;
import com.matchskill.backend.service.RateLimiterService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class, properties = {
        "spring.security.oauth2.client.registration.google.client-id=",
        "spring.security.oauth2.client.registration.google.client-secret="
})
class SecurityConfigurationTest extends SecurityConfigurationSupport {

    @Test
    void shouldStartWithRealAuthenticationDependenciesAndGoogleDisabled() {
        assertThat(context.getBean(AuthService.class)).isNotNull();
        assertThat(context.getBean(GoogleOAuth2SuccessHandler.class)).isNotNull();
        assertThat(context.getBeansOfType(ClientRegistrationRepository.class)).isEmpty();
    }

}

@WebMvcTest(controllers = AuthController.class, properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
class GoogleSecurityConfigurationTest extends SecurityConfigurationSupport {

    @Test
    void shouldPreserveGoogleAuthorizationAndCallbackRoutes() throws Exception {
        String location = mvc.perform(get("/api/auth/google")
                        .contextPath("/api").servletPath("/auth/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=test-client")
                .contains("redirect_uri=http://localhost/api/auth/google/callback")
                .contains("state=");
        var registration = context.getBean(ClientRegistrationRepository.class)
                .findByRegistrationId("google");
        assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
    }

    @Test
    void shouldHandleGoogleCallbackFailureAsJson() throws Exception {
        mvc.perform(get("/api/auth/google/callback")
                        .contextPath("/api").servletPath("/auth/google/callback")
                        .param("error", "access_denied").param("state", "unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().doesNotExist("Location"));
    }
}

@WebMvcTest(controllers = AuthController.class, properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret="
})
class IncompleteGoogleSecurityConfigurationTest extends SecurityConfigurationSupport {

    @Test
    void shouldNotRegisterGoogleWithIncompleteCredentials() {
        assertThat(context.getBeansOfType(ClientRegistrationRepository.class)).isEmpty();
    }
}

@Import({SecurityConfig.class, AuthService.class, JwtService.class,
        JwtAuthenticationFilter.class, GoogleOAuth2SuccessHandler.class, RateLimitInterceptor.class})
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class,
        OAuth2Properties.class, RateLimitProperties.class})
abstract class SecurityConfigurationSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FilterChainProxy filters;

    @MockBean
    UserRepository users;

    @MockBean
    RateLimiterService rateLimiterService;

    @BeforeEach
    void allowRateLimitedRequests() {
        when(rateLimiterService.tryConsume(anyString(), anyInt(), anyInt())).thenReturn(true);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectMissingBearerWithJsonEvenForHtmlAcceptHeader() throws Exception {
        mvc.perform(get("/api/auth/me").contextPath("/api").servletPath("/auth/me")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void shouldRejectInvalidBearerWithJson() throws Exception {
        mvc.perform(get("/api/auth/me").contextPath("/api").servletPath("/auth/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldAcceptExistingBearerContract() throws Exception {
        User user = user();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        mvc.perform(get("/api/auth/me").contextPath("/api").servletPath("/auth/me")
                        .header("Authorization", "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void shouldKeepLocalLoginUsableWithConfiguredServletPaths() throws Exception {
        User user = user();
        user.setPasswordHash(passwordEncoder.encode("Example1!"));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        String body = mvc.perform(post("/api/auth/login")
                        .contextPath("/api").servletPath("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"local@example.com","password":"Example1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).get("token").asText();
        assertThat(jwtService.validateAndGetUserId(token)).contains(user.getId());
        assertThat(objectMapper.readTree(body).size()).isEqualTo(1);
    }

    @Test
    void shouldTranslateAccessDeniedFromSecurityFiltersToJson() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of()));
        var request = new MockHttpServletRequest("GET", "/auth/me");
        var response = new MockHttpServletResponse();
        var translator = filters.getFilters("/auth/me").stream()
                .filter(ExceptionTranslationFilter.class::isInstance)
                .map(ExceptionTranslationFilter.class::cast).findFirst().orElseThrow();

        translator.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AccessDeniedException("Denied by downstream security filter");
        });

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("code").asText())
                .isEqualTo("FORBIDDEN");
    }

    User user() {
        return User.builder().id(UUID.randomUUID()).email("local@example.com")
                .displayName("Local user").timeZone("UTC").build();
    }
}
