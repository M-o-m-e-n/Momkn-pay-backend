package com.momknpay.session.service;

import com.momknpay.common.util.TimeProvider;
import com.momknpay.session.repository.SessionRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes sessions that expired more than a day ago (LLD §4.3). Their nonces cascade; inquiries
 * keep {@code session_id = NULL}. Safe to run twice.
 */
@Component
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);
    private static final Duration GRACE = Duration.ofDays(1);

    private final SessionRepository sessions;
    private final TimeProvider time;

    public SessionCleanupJob(SessionRepository sessions, TimeProvider time) {
        this.sessions = sessions;
        this.time = time;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    @Transactional
    public int purge() {
        int deleted = sessions.deleteExpiredBefore(time.now().minus(GRACE));
        if (deleted > 0) {
            log.info("session.purged count={}", deleted);
        }
        return deleted;
    }
}
