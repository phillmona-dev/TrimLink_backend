package com.trimlink.common.ratelimit;

import com.trimlink.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Redis-based sliding window rate limiter.
 *
 * Algorithm (Sorted Set per key):
 *  1. Remove all timestamps older than (now - windowSeconds)
 *  2. Count remaining timestamps in the set
 *  3. If count >= maxRequests → rate limit exceeded → throw 429
 *  4. Otherwise add current timestamp with score=timestamp (ZADD)
 *  5. Set TTL = windowSeconds to auto-clean idle keys
 *
 * Atomic: steps 1-5 are executed as a single Lua script.
 *
 * Example for OTP:  maxRequests=3, windowSeconds=600 (10 min)
 *   → max 3 OTP sends per phone number per 10 minutes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua script for atomic sliding window check + record.
     * Returns the current count AFTER adding the new timestamp.
     * If count > maxRequests, calling code should reject.
     */
    private static final String SLIDING_WINDOW_LUA = """
            local key        = KEYS[1]
            local now        = tonumber(ARGV[1])
            local window     = tonumber(ARGV[2])
            local max_reqs   = tonumber(ARGV[3])
            local expire_at  = tonumber(ARGV[4])
            
            -- Remove timestamps outside window
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000)
            
            -- Count current requests in window
            local count = redis.call('ZCARD', key)
            
            if count >= max_reqs then
                return count
            end
            
            -- Add current request timestamp
            redis.call('ZADD', key, now, now)
            redis.call('EXPIREAT', key, expire_at)
            
            return count + 1
            """;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(
            SLIDING_WINDOW_LUA, Long.class);

    /**
     * Check and record a rate-limited action.
     *
     * @param key         Unique key (e.g. "rate:otp:+251912345678")
     * @param maxRequests Maximum allowed requests in the window
     * @param windowSecs  Sliding window duration in seconds
     * @throws RateLimitExceededException if limit is exceeded
     */
    public void check(String key, int maxRequests, long windowSecs) {
        long nowMs     = Instant.now().toEpochMilli();
        long expireAt  = Instant.now().getEpochSecond() + windowSecs + 1;

        Long count = stringRedisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(windowSecs),
                String.valueOf(maxRequests),
                String.valueOf(expireAt)
        );

        if (count == null || count > maxRequests) {
            log.warn("Rate limit exceeded for key={}", key);
            throw new RateLimitExceededException(
                    String.format("Too many requests. You can try again in %d minutes.",
                            windowSecs / 60),
                    windowSecs
            );
        }

        log.debug("Rate limit check: key={}, count={}/{}", key, count, maxRequests);
    }

    // ─── Convenience methods ───────────────────────────────────────────────

    /** OTP send: max 3 per 10 minutes per phone number. */
    public void checkOtpSend(String phoneNumber) {
        check("rate:otp:" + phoneNumber, 3, 600);
    }

    /** Generic API: max N per minute per IP. */
    public void checkApiRequest(String ip, int maxPerMinute) {
        check("rate:api:" + ip, maxPerMinute, 60);
    }
}
