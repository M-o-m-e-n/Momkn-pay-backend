package com.momknpay.payment.service;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.common.config.AppProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Mock rule: services whose id ends in {@code _slow} answer after {@code app.slow-delay} (8 s) so
 * clients must show a real loading state and a timeout path (FR-MCK-2).
 *
 * <p>The only place allowed to sleep. Callers apply it after their database work has committed, so
 * no lock or connection is held; requests run on virtual threads, so other calls are not blocked.
 */
@Component
public class SlowServiceDelay {

    private final Duration delay;

    public SlowServiceDelay(AppProperties properties) {
        this.delay = properties.slowDelay();
    }

    public void applyIf(BillerService service) {
        if (service != null && service.isSlow() && !delay.isZero()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
