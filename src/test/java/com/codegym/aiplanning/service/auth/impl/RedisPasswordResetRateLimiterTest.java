package com.codegym.aiplanning.service.auth.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisPasswordResetRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisPasswordResetRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisPasswordResetRateLimiter(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isAllowedByEmail_WhenFirstRequest_ReturnsTrue() {
        // Arrange
        String email = "test@example.com";
        String key = "ai-planning:auth:rate_limit:pwd_reset:email:" + email;
        when(valueOperations.setIfAbsent(eq(key), eq("1"), any(Duration.class))).thenReturn(true);

        // Act
        boolean allowed = rateLimiter.isAllowedByEmail(email);

        // Assert
        assertTrue(allowed);
        verify(valueOperations).setIfAbsent(eq(key), eq("1"), eq(Duration.ofMinutes(3)));
    }

    @Test
    void isAllowedByEmail_WhenRequestExistsInWindow_ReturnsFalse() {
        // Arrange
        String email = "test@example.com";
        String key = "ai-planning:auth:rate_limit:pwd_reset:email:" + email;
        when(valueOperations.setIfAbsent(eq(key), eq("1"), any(Duration.class))).thenReturn(false);

        // Act
        boolean allowed = rateLimiter.isAllowedByEmail(email);

        // Assert
        assertFalse(allowed);
    }

    @Test
    void isAllowedByEmail_WhenRedisExceptionOccurs_ReturnsTrueToNotBlock() {
        // Arrange
        String email = "test@example.com";
        String key = "ai-planning:auth:rate_limit:pwd_reset:email:" + email;
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenThrow(new RuntimeException("Redis down"));

        // Act
        boolean allowed = rateLimiter.isAllowedByEmail(email);

        // Assert
        assertTrue(allowed, "Should return true on Redis error to allow legitimate requests");
    }

    @Test
    void isAllowedByIp_WhenUnderLimit_ReturnsTrue() {
        // Arrange
        String ip = "127.0.0.1";
        String key = "ai-planning:auth:rate_limit:pwd_reset:ip:" + ip;
        when(valueOperations.increment(key)).thenReturn(1L);

        // Act
        boolean allowed = rateLimiter.isAllowedByIp(ip);

        // Assert
        assertTrue(allowed);
        verify(redisTemplate).expire(eq(key), eq(Duration.ofHours(1)));
    }

    @Test
    void isAllowedByIp_WhenOverLimit_ReturnsFalse() {
        // Arrange
        String ip = "127.0.0.1";
        String key = "ai-planning:auth:rate_limit:pwd_reset:ip:" + ip;
        when(valueOperations.increment(key)).thenReturn(21L); // Limit is 20

        // Act
        boolean allowed = rateLimiter.isAllowedByIp(ip);

        // Assert
        assertFalse(allowed);
    }
}
