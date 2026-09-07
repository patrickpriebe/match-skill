package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.matchskill.backend.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    private static final String KEY = "ratelimit:login:127.0.0.1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @ParameterizedTest
    @CsvSource({"1,true", "3,true", "5,true", "6,false"})
    void shouldApplyInclusiveLimitUsingOneAtomicScript(long count, boolean allowed) {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(KEY)), eq("60"))).thenReturn(count);

        assertThat(rateLimiterService.tryConsume(KEY, 5, 60)).isEqualTo(allowed);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), eq("60"));
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    void shouldFailClosedWhenRedisReturnsNoCounter() {
        assertUnavailable();
    }

    @Test
    void shouldFailClosedWhenRedisConnectionFails() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(KEY)), eq("60")))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        assertUnavailable();
    }

    @ParameterizedTest
    @CsvSource({"0,60", "-1,60", "5,0", "5,-1"})
    void shouldRejectInvalidLimitsWithoutAccessingRedis(int maximum, int window) {
        assertThatThrownBy(() -> rateLimiterService.tryConsume(KEY, maximum, window))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate limits and windows must be positive");
        verifyNoInteractions(redisTemplate);
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> rateLimiterService.tryConsume(KEY, 5, 60))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("RATE_LIMIT_UNAVAILABLE");
                });
    }
}
