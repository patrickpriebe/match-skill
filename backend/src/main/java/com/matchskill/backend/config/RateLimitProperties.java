package com.matchskill.backend.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Max requests per window, per client IP, for a rate-limited route. */
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public record RateLimitProperties(
        @Positive int loginMaxRequests,
        @Positive int loginWindowSeconds,
        @Positive int registerMaxRequests,
        @Positive int registerWindowSeconds,
        @Positive int createExchangeMaxRequests,
        @Positive int createExchangeWindowSeconds) {}
