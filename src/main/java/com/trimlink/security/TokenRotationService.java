package com.trimlink.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenRotationService {

    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_STATE_PREFIX = "token_state:";
    private static final String USER_TOKENS_PREFIX = "user_tokens:";

    public enum TokenState { ACTIVE, USED, REVOKED }

    /**
     * Registers a new refresh token as ACTIVE.
     */
    public void registerToken(String userId, String jti, long expiryMs) {
        String stateKey = TOKEN_STATE_PREFIX + jti;
        redisTemplate.opsForValue().set(stateKey, TokenState.ACTIVE.name(), expiryMs, TimeUnit.MILLISECONDS);
        
        // Track all tokens for this user for mass revocation
        String userKey = USER_TOKENS_PREFIX + userId;
        redisTemplate.opsForSet().add(userKey, jti);
        redisTemplate.expire(userKey, 30, TimeUnit.DAYS);
    }

    /**
     * Validates and rotates a token.
     * @return true if valid and rotated, false if invalid or breach detected.
     */
    public boolean validateAndRotate(String userId, String jti) {
        String stateKey = TOKEN_STATE_PREFIX + jti;
        String state = redisTemplate.opsForValue().get(stateKey);

        if (state == null) {
            log.warn("Token {} not found in registry (expired or never registered)", jti);
            return false;
        }

        if (TokenState.USED.name().equals(state)) {
            log.error("BREACH DETECTED: Token {} was already used! Revoking all tokens for user {}", jti, userId);
            revokeAllUserTokens(userId);
            return false;
        }

        if (TokenState.REVOKED.name().equals(state)) {
            log.warn("Token {} is revoked", jti);
            return false;
        }

        // Mark as used
        redisTemplate.opsForValue().set(stateKey, TokenState.USED.name(), 1, TimeUnit.HOURS);
        return true;
    }

    /**
     * Revokes all refresh tokens for a user.
     */
    public void revokeAllUserTokens(String userId) {
        String userKey = USER_TOKENS_PREFIX + userId;
        Set<String> jtis = redisTemplate.opsForSet().members(userKey);
        if (jtis != null) {
            for (String jti : jtis) {
                redisTemplate.opsForValue().set(TOKEN_STATE_PREFIX + jti, TokenState.REVOKED.name(), 1, TimeUnit.HOURS);
            }
        }
        redisTemplate.delete(userKey);
    }
}
