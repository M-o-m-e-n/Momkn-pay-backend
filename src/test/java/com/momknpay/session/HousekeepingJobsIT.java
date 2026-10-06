package com.momknpay.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.session.service.NonceCleanupJob;
import com.momknpay.session.service.SessionCleanupJob;
import com.momknpay.session.service.SessionService;
import com.momknpay.session.web.dto.CreateSessionResponse;
import com.momknpay.support.PaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The cleanup jobs of LLD §4.3 delete only what is old enough, and keep history intact. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HousekeepingJobsIT {

    @Autowired private MockMvc mvc;
    @Autowired private SessionService sessionService;
    @Autowired private NonceCleanupJob nonceCleanup;
    @Autowired private SessionCleanupJob sessionCleanup;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void noncesOlderThanTheRetentionAreForgotten() throws Exception {
        CreateSessionResponse session = sessionService.create("usr_01");
        new PaymentClient(mvc, "usr_01", session).openInquiry("svc_elec_cairo", "1024750891");
        String nonce = nonceOf(session.sessionId());

        nonceCleanup.purge();
        assertThat(nonceCount(nonce)).as("fresh nonce kept").isEqualTo(1);

        jdbc.update(
                "UPDATE used_nonces SET created_at = now() - interval '6 minutes' WHERE nonce = ?",
                nonce);
        nonceCleanup.purge();
        assertThat(nonceCount(nonce)).as("old nonce purged").isZero();
    }

    @Test
    void sessionsExpiredForADayAreDeletedButTheirInquiriesStay() throws Exception {
        CreateSessionResponse session = sessionService.create("usr_01");
        String inquiryId =
                new PaymentClient(mvc, "usr_01", session)
                        .openInquiry("svc_elec_cairo", "1024750891");

        jdbc.update(
                "UPDATE sessions SET expires_at = now() - interval '2 days' WHERE id = ?",
                session.sessionId());
        sessionCleanup.purge();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sessions WHERE id = ?",
                                Integer.class,
                                session.sessionId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM used_nonces WHERE session_id = ?",
                                Integer.class,
                                session.sessionId()))
                .as("nonces cascade")
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT session_id FROM inquiries WHERE id = ?",
                                String.class,
                                inquiryId))
                .as("inquiry kept, session reference cleared")
                .isNull();
    }

    @Test
    void recentlyExpiredSessionsAreKept() {
        CreateSessionResponse session = sessionService.create("usr_01");
        jdbc.update(
                "UPDATE sessions SET expires_at = now() - interval '1 hour' WHERE id = ?",
                session.sessionId());

        sessionCleanup.purge();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sessions WHERE id = ?",
                                Integer.class,
                                session.sessionId()))
                .isEqualTo(1);
    }

    private String nonceOf(String sessionId) {
        return jdbc.queryForObject(
                "SELECT nonce FROM used_nonces WHERE session_id = ?", String.class, sessionId);
    }

    private int nonceCount(String nonce) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM used_nonces WHERE nonce = ?", Integer.class, nonce);
    }
}
