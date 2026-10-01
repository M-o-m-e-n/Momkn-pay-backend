package com.momknpay.payment;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.web.Headers;
import com.momknpay.support.Payloads;
import com.momknpay.support.PaymentClient;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /v1/payments/confirm: happy path and idempotency (FR-PAY-1…5, FR-PAY-9…10). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ConfirmIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private PaymentClient mina;

    @BeforeEach
    void newClient() {
        mina = new PaymentClient(mvc, "usr_01");
    }

    @Test
    void confirmPaysTheInquiryOnce(CapturedOutput output) throws Exception {
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750891");
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);

        String body =
                mina.confirm(inquiryId, UUID.randomUUID(), "1234")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.transactionId").value(matchesPattern("^txn_\\d+$")))
                        .andExpect(jsonPath("$.status").value("SUCCESS"))
                        .andExpect(
                                jsonPath("$.reference")
                                        .value(matchesPattern("^MP-" + today + "-\\d{4,}$")))
                        .andExpect(jsonPath("$.paidAt").isString())
                        .andExpect(jsonPath("$.total").value(25320))
                        .andExpect(jsonPath("$.service.id").value("svc_elec_cairo"))
                        .andExpect(jsonPath("$.service.nameEn").value("Cairo Electricity"))
                        .andExpect(jsonPath("$.service.nameAr").value("كهرباء القاهرة"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String transactionId = JsonPath.read(body, "$.transactionId");
        assertThat(inquiryStatus(inquiryId)).isEqualTo("CONFIRMED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM transactions WHERE id = ?",
                                String.class,
                                transactionId))
                .isEqualTo("SUCCESS");
        assertThat(output.getAll()).contains("payment.confirmed").doesNotContain("\"1234\"");
    }

    @Test
    void retryWithTheSameBytesReturnsTheSameTransaction() throws Exception {
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750891");
        UUID key = UUID.randomUUID();
        String payload = Payloads.confirm("1234");

        String first = transactionId(mina.confirmWithPayload(inquiryId, key, payload));
        String retry =
                transactionId(mina.confirmWithPayload(inquiryId, key, payload)); // same nonce
        String fresh = transactionId(mina.confirm(inquiryId, key, "1234")); // new nonce, same key

        assertThat(retry).isEqualTo(first);
        assertThat(fresh).isEqualTo(first);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM transactions WHERE idempotency_key = ?",
                                Integer.class,
                                key))
                .isEqualTo(1);
    }

    @Test
    void keyReusedForAnotherInquiryIsAConflict() throws Exception {
        String first = mina.openInquiry("svc_elec_cairo", "1024750891");
        String second = mina.openInquiry("svc_elec_cairo", "1024750892");
        UUID key = UUID.randomUUID();
        mina.confirm(first, key, "1234").andExpect(status().isOk());

        mina.confirm(second, key, "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void aPaidInquiryCannotBePaidAgainWithANewKey() throws Exception {
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750891");
        mina.confirm(inquiryId, UUID.randomUUID(), "1234").andExpect(status().isOk());

        mina.confirm(inquiryId, UUID.randomUUID(), "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INQUIRY_ALREADY_CONFIRMED"));
    }

    @Test
    void idempotencyKeyIsRequiredAndMustBeAUuid() throws Exception {
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750891");

        mina.confirm(inquiryId, null, "1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("Idempotency-Key"));
        mvc.perform(
                        withClientHeaders(post("/v1/payments/confirm"))
                                .header(Headers.USER_ID, "usr_01")
                                .header(Headers.IDEMPOTENCY_KEY, "not-a-uuid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"inquiryId\":\"" + inquiryId + "\",\"payload\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("Idempotency-Key"));
    }

    @Test
    void unknownOrForeignInquiryIsNotFound() throws Exception {
        String minasInquiry = mina.openInquiry("svc_elec_cairo", "1024750891");
        PaymentClient sara = new PaymentClient(mvc, "usr_02");

        mina.confirm("inq_0000000000000000", UUID.randomUUID(), "1234")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"));
        sara.confirm(minasInquiry, UUID.randomUUID(), "1234")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"));
    }

    private String inquiryStatus(String inquiryId) {
        return jdbc.queryForObject(
                "SELECT status FROM inquiries WHERE id = ?", String.class, inquiryId);
    }

    private static String transactionId(ResultActions result) throws Exception {
        return JsonPath.read(
                result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.transactionId");
    }
}
