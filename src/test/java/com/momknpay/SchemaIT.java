package com.momknpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** The database itself enforces the invariants (NFR-REL-1…3), not only the Java code. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional // every test rolls back
class SchemaIT {

    private static final String USER = "usr_schema";
    private static final String SERVICE = "svc_schema_test";
    private static final String INQUIRY = "inq_schema";

    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void insertFixtures() {
        jdbc.update(
                "INSERT INTO users (id, full_name, mobile, email, pin_hash) VALUES (?,?,?,?,?)",
                USER,
                "Schema Test",
                "01099999999",
                "schema@example.com",
                "$2a$12$hash");
        jdbc.update(
                """
                INSERT INTO services (id, name_en, name_ar, category, icon_url, input_label,
                                      input_pattern, min_amount, max_amount)
                VALUES (?, 'Schema', 'مخطط', 'water', 'https://x', 'Account', '^[0-9]{9}$', 500, 300000)
                """,
                SERVICE);
        insertInquiry(INQUIRY, 24750, 500, 70, 25320);
    }

    @Test
    void idempotencyKeyIsUniquePerUser() {
        UUID key = UUID.randomUUID();
        insertTransaction(5000, key, "FAILED", "INSUFFICIENT_BALANCE");

        assertThatThrownBy(() -> insertTransaction(5001, key, "FAILED", "INSUFFICIENT_BALANCE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_txn_idempotency");
    }

    @Test
    void inquiryAcceptsManyFailedButOnlyOneSettledTransaction() {
        insertTransaction(5000, UUID.randomUUID(), "FAILED", "INSUFFICIENT_BALANCE");
        insertTransaction(5001, UUID.randomUUID(), "SUCCESS", null);

        assertThatThrownBy(() -> insertTransaction(5002, UUID.randomUUID(), "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_txn_inquiry_settled");
    }

    @Test
    void inquiryTotalMustEqualItsParts() {
        assertThatThrownBy(() -> insertInquiry("inq_bad_total", 24750, 500, 70, 99999))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inquiry_total");
    }

    @Test
    void failedTransactionRequiresFailureCode() {
        assertThatThrownBy(() -> insertTransaction(5000, UUID.randomUUID(), "FAILED", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_txn_failure");
    }

    @Test
    void mobileMustBeEgyptianFormat() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO users (id, full_name, mobile, email, pin_hash)"
                                            + " VALUES ('usr_bad', 'Bad', '0301234567', 'b@x.io',"
                                            + " 'h')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatingAServiceBumpsUpdatedAt() {
        jdbc.update(
                "UPDATE services SET updated_at = '2000-01-01T00:00:00Z' WHERE id = ?", SERVICE);

        jdbc.update("UPDATE services SET name_en = 'Renamed' WHERE id = ?", SERVICE);

        Timestamp updatedAt =
                jdbc.queryForObject(
                        "SELECT updated_at FROM services WHERE id = ?", Timestamp.class, SERVICE);
        assertThat(updatedAt.toInstant()).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }

    private void insertInquiry(String id, long amountDue, long fee, long vat, long total) {
        jdbc.update(
                """
                INSERT INTO inquiries (id, user_id, service_id, subscriber_number, customer_name,
                                       bill_month, amount_due, service_fee, vat, total, rule,
                                       created_at, expires_at)
                VALUES (?, ?, ?, '1024750891', 'Mina A.', '2026-08', ?, ?, ?, ?, 'NORMAL',
                        now(), now() + interval '5 minutes')
                """,
                id,
                USER,
                SERVICE,
                amountDue,
                fee,
                vat,
                total);
    }

    private void insertTransaction(long seq, UUID key, String status, String failureCode) {
        jdbc.update(
                """
                INSERT INTO transactions (id, seq, user_id, inquiry_id, service_id, idempotency_key,
                                          status, failure_code, reference, subscriber_number,
                                          customer_name, bill_month, amount_due, service_fee, vat,
                                          total, created_at, paid_at, pending_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '1024750891', 'Mina A.', '2026-08',
                        24750, 500, 70, 25320, now(),
                        CASE WHEN ? = 'SUCCESS' THEN now() END,
                        CASE WHEN ? = 'PENDING' THEN now() END)
                """,
                "txn_" + seq,
                seq,
                USER,
                INQUIRY,
                SERVICE,
                key,
                status,
                failureCode,
                "MP-20260920-" + seq,
                status,
                status);
    }
}
