package com.matchskill.backend.service;

import com.matchskill.backend.exception.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Atomically increments and expires each fixed-window counter. */
@Service
public class RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryConsume(String key, int maxRequests, int windowSeconds) {
        if (maxRequests <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("Rate limits and windows must be positive");
        }
        try {
            Long count = redisTemplate.execute(CONSUME_SCRIPT, List.of(key), Integer.toString(windowSeconds));
            if (count != null) {
                return count <= maxRequests;
            }
        } catch (DataAccessException ex) {
            logger.warn("Rate limit storage is unavailable: {}", ex.getClass().getSimpleName());
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE",
                "Request protection is temporarily unavailable, try again later");
    }
}
