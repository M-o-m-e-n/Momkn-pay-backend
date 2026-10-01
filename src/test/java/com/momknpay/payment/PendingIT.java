package com.momknpay.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import com.momknpay.support.PaymentClient;
import com.momknpay.transaction.service.PendingResolver;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Mock rule 8 end to end: PENDING, then SUCCESS after the delay (FR-TXN-5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PendingIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PendingResolver pendingResolver;

    private PaymentClient mina;

    @BeforeEach
    void newClient() {
        mina = new PaymentClient(mvc, "usr_01");
    }

    @Test
    void pendingPaymentBecomesSuccessOnReplayOnceDue() throws Exception {
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750898");
        UUID key = UUID.randomUUID();

        String body =
                mina.confirm(inquiryId, key, "1234")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andExpect(jsonPath("$.paidAt").value(nullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String transactionId = JsonPath.read(body, "$.transactionId");
        Instant pendingUntil = pendingUntil(transactionId);
        assertThat(Duration.between(Instant.now(), pendingUntil))
                .isBetween(Duration.ofSeconds(8), Duration.ofSeconds(10));

        mina.confirm(inquiryId, key, "1234") // replay before it is due
                .andExpect(jsonPath("$.status").value("PENDING"));

        Instant due = makeDue(transactionId);
        mina.confirm(inquiryId, key, "1234") // replay after it is due
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paidAt").value(due.toString()));
        assertThat(row(transactionId)).containsEntry("status", "SUCCESS");
    }

    @Test
    void sweepResolvesRowsNobodyReads() throws Exception {
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750898");
        String body =
                mina.confirm(inquiryId, UUID.randomUUID(), "1234")
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String transactionId = JsonPath.read(body, "$.transactionId");
        Instant due = makeDue(transactionId);

        pendingResolver.sweep(); // the scheduled sweep may have got there first; either way

        Map<String, Object> row = row(transactionId);
        assertThat(row).containsEntry("status", "SUCCESS");
        assertThat(((Timestamp) row.get("paid_at")).toInstant()).isEqualTo(due);
        assertThat(pendingResolver.sweep()).isZero(); // idempotent
    }

    @Test
    void aPendingInquiryCannotBePaidAgain() throws Exception {
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750898");
        mina.confirm(inquiryId, UUID.randomUUID(), "1234").andExpect(status().isOk());

        mina.confirm(inquiryId, UUID.randomUUID(), "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INQUIRY_ALREADY_CONFIRMED"));
    }

    /** Moves the due time into the past instead of sleeping 10 s. */
    private Instant makeDue(String transactionId) {
        Instant due = Instant.parse("2026-01-01T00:00:10Z");
        jdbc.update(
                "UPDATE transactions SET pending_until = ? WHERE id = ?",
                Timestamp.from(due),
                transactionId);
        return due;
    }

    private Instant pendingUntil(String transactionId) {
        return ((Timestamp) row(transactionId).get("pending_until")).toInstant();
    }

    private Map<String, Object> row(String transactionId) {
        return jdbc.queryForMap("SELECT * FROM transactions WHERE id = ?", transactionId);
    }
}
