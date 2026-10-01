package com.momknpay.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.momknpay.common.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets: N attempts per minute per (policy, user) (NFR-SEC-9). Resets on restart
 * and is per instance — an accepted limitation (SRS §8, L-4).
 */
@Component
public class RateLimiter {

    /** Which endpoint a bucket belongs to; each user gets one bucket per policy. */
    public enum Policy {
        SESSIONS,
        CONFIRM
    }

    private static final Duration PERIOD = Duration.ofMinutes(1);

    private final int perMinute;
    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder()
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .maximumSize(10_000)
                    .build();

    public RateLimiter(AppProperties properties) {
        this.perMinute = properties.rateLimit().perMinute();
    }

    /**
     * @throws RateLimitedException when the user has no attempts left for this policy
     */
    public void consume(Policy policy, String userId) {
        Bucket bucket = buckets.get(policy + ":" + userId, key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            // round up to whole seconds: exactly 60 s left is 60, not 61
            long seconds =
                    Math.ceilDiv(probe.getNanosToWaitForRefill(), TimeUnit.SECONDS.toNanos(1));
            throw new RateLimitedException(seconds);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(
                        Bandwidth.builder()
                                .capacity(perMinute)
                                .refillIntervally(perMinute, PERIOD)
                                .build())
                .build();
    }
}
