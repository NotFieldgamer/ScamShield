package com.scamshield.ratelimit;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limiter backed by Redis. Each caller key (IP for anonymous, user for
 * authenticated) gets a bucket that refills continuously; a request costs one token.
 *
 * <p>The check-and-decrement runs as a single Lua script so it is atomic under concurrency.
 * Fails <em>open</em> (allows the request) if Redis is unreachable — a public analyzer should not
 * 500 because the rate-limit store blipped — and logs it. Capacity/refill are conservative
 * defaults suitable for a free-tier demo.
 */
@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    // capacity tokens, refilled at refillPerSecond; classic token bucket in Redis.
    private static final String LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local tokens = tonumber(redis.call('hget', key, 'tokens'))
            local ts = tonumber(redis.call('hget', key, 'ts'))
            if tokens == nil then tokens = capacity; ts = now end
            local elapsed = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + elapsed * refill)
            local allowed = 0
            if tokens >= 1 then tokens = tokens - 1; allowed = 1 end
            redis.call('hset', key, 'tokens', tokens, 'ts', now)
            redis.call('pexpire', key, math.ceil(capacity / refill * 1000))
            return allowed
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;
    private final double capacity;
    private final double refillPerSecond;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
        this.capacity = 60;          // burst of 60 requests
        this.refillPerSecond = 1.0;  // sustained 60/min
    }

    public boolean tryAcquire(String callerKey) {
        String key = "ratelimit:" + callerKey;
        try {
            Long allowed = redis.execute(script, List.of(key),
                    String.valueOf(capacity), String.valueOf(refillPerSecond),
                    String.valueOf(System.currentTimeMillis() / 1000.0));
            return allowed != null && allowed == 1L;
        } catch (RuntimeException e) {
            log.warn("rate limiter unavailable, failing open for key {}: {}", callerKey, e.toString());
            return true;
        }
    }
}
