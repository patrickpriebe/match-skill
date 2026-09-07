package com.matchskill.backend.security;

import com.matchskill.backend.config.RateLimitProperties;
import com.matchskill.backend.controller.AuthController;
import com.matchskill.backend.controller.ExchangeController;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Limits public authentication by client IP and exchange creation by authenticated user. */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimiterService rateLimiterService, RateLimitProperties properties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String route = routeFor(request, handler);
        if (route == null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String client = "create-exchange".equals(route) && authentication != null
                && authentication.getPrincipal() instanceof java.util.UUID userId
                ? userId.toString() : request.getRemoteAddr();
        String key = "ratelimit:" + route + ":" + client;
        boolean allowed =
                switch (route) {
                    case "login" ->
                            rateLimiterService.tryConsume(
                                    key, properties.loginMaxRequests(), properties.loginWindowSeconds());
                    case "register" ->
                            rateLimiterService.tryConsume(
                                    key, properties.registerMaxRequests(), properties.registerWindowSeconds());
                    case "create-exchange" ->
                            rateLimiterService.tryConsume(
                                    key,
                                    properties.createExchangeMaxRequests(),
                                    properties.createExchangeWindowSeconds());
                    default -> true;
                };
        if (!allowed) {
            int windowSeconds = switch (route) {
                case "login" -> properties.loginWindowSeconds();
                case "register" -> properties.registerWindowSeconds();
                default -> properties.createExchangeWindowSeconds();
            };
            response.setHeader("Retry-After", Integer.toString(windowSeconds));
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests, try again later");
        }
        return true;
    }

    private String routeFor(HttpServletRequest request, Object handler) {
        // Resolve the mapped method so path parameters cannot bypass the limiter.
        if (handler instanceof HandlerMethod handlerMethod) {
            if (AuthController.class.isAssignableFrom(handlerMethod.getBeanType())) {
                return switch (handlerMethod.getMethod().getName()) {
                    case "login" -> "login";
                    case "register" -> "register";
                    default -> null;
                };
            }
            if (ExchangeController.class.isAssignableFrom(handlerMethod.getBeanType())
                    && "create".equals(handlerMethod.getMethod().getName())) {
                return "create-exchange";
            }
            return null;
        }
        String method = request.getMethod();
        String path = request.getServletPath();
        if ("POST".equals(method) && "/auth/login".equals(path)) {
            return "login";
        }
        if ("POST".equals(method) && "/auth/register".equals(path)) {
            return "register";
        }
        if ("POST".equals(method) && "/exchanges".equals(path)) {
            return "create-exchange";
        }
        return null;
    }

}
