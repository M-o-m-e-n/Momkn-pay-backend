package com.momknpay.payload;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.payload.service.NonceCleanupJob;
import com.momknpay.payload.service.ReplayGuard;
import com.momknpay.support.TestCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** The nonce cleanup of LLD §4.3 forgets only nonces older than the retention (5 min). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NonceCleanupIT {

    @Autowired private ReplayGuard replayGuard;
    @Autowired private NonceCleanupJob nonceCleanup;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void noncesOlderThanTheRetentionAreForgotten() {
        String nonce = TestCrypto.nonce();
        replayGuard.check(nonce, TestCrypto.nowTs());

        nonceCleanup.purge();
        assertThat(count(nonce)).as("fresh nonce kept").isEqualTo(1);

        jdbc.update(
                "UPDATE used_nonces SET created_at = now() - interval '1 hour' WHERE nonce = ?",
                nonce);
        nonceCleanup.purge();
        assertThat(count(nonce)).as("old nonce purged").isZero();
    }

    private int count(String nonce) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM used_nonces WHERE nonce = ?", Integer.class, nonce);
    }
}
