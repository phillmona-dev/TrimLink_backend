package com.trimlink.module.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_DURATION_MINUTES = 5;
    private static final String ATTEMPT_PREFIX = "login_attempt:";
    private static final String BLOCK_PREFIX = "login_block:";

    /**
     * Increments failure count for a given key (IP or Username).
     */
    public void loginFailed(String key) {
        String attemptKey = ATTEMPT_PREFIX + key;
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        
        if (attempts == null || attempts == 1) {
            redisTemplate.expire(attemptKey, BLOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            log.warn("Blocking {} due to {} failed login attempts", key, attempts);
            redisTemplate.opsForValue().set(BLOCK_PREFIX + key, "blocked", BLOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * Clears failure count on successful login.
     */
    public void loginSucceeded(String key) {
        redisTemplate.delete(ATTEMPT_PREFIX + key);
        redisTemplate.delete(BLOCK_PREFIX + key);
    }

    /**
     * Checks if a given key is currently blocked.
     */
    public boolean isBlocked(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCK_PREFIX + key));
    }
}
