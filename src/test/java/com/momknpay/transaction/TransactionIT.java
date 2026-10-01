package com.momknpay.transaction;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.web.Headers;
import com.momknpay.support.PaymentClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET /v1/payments/transactions[/{id}] (FR-TXN-1…5, FR-SEED-4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void historyIsNewestFirstAndContainsTheSeededPayments() throws Exception {
        String body =
                list("usr_01", 0, 50)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        List<String> ids = JsonPath.read(body, "$.items[*].transactionId");
        assertThat(ids)
                .containsSubsequence(
                        "txn_5500", "txn_5501", "txn_5502", "txn_5503", "txn_5504", "txn_5505");
        List<String> created = JsonPath.read(body, "$.items[*].createdAt");
        assertThat(created.stream().map(Instant::parse).toList())
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat((Integer) JsonPath.read(body, "$.page")).isZero();
        assertThat((Integer) JsonPath.read(body, "$.totalItems")).isGreaterThanOrEqualTo(6);
    }

    @Test
    void historyRowsCarryTheContractFields() throws Exception {
        String body = list("usr_01", 0, 50).andReturn().getResponse().getContentAsString();
        String row = "$.items[?(@.transactionId == 'txn_5502')]";

        assertThat((List<String>) JsonPath.read(body, row + ".status")).containsExactly("FAILED");
        assertThat((List<String>) JsonPath.read(body, row + ".failureCode"))
                .containsExactly("INSUFFICIENT_BALANCE");
        assertThat((List<Object>) JsonPath.read(body, row + ".paidAt"))
                .containsExactly((Object) null);
        assertThat((List<Integer>) JsonPath.read(body, row + ".total")).containsExactly(14830);
        assertThat((List<String>) JsonPath.read(body, row + ".service.nameAr"))
                .containsExactly("مياه القاهرة الكبرى");
        assertThat((List<String>) JsonPath.read(body, row + ".service.category"))
                .containsExactly("water");
    }

    @Test
    void freshAccountHasAnEmptyHistory() throws Exception {
        list("usr_02", 0, 20)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void pagesAreSizedAndCounted() throws Exception {
        String body =
                list("usr_01", 1, 2)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        int total = JsonPath.read(body, "$.totalItems");
        assertThat((List<?>) JsonPath.read(body, "$.items")).hasSize(2);
        assertThat((Integer) JsonPath.read(body, "$.size")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(body, "$.totalPages")).isEqualTo((total + 1) / 2);
    }

    @Test
    void invalidPagingIsNamed() throws Exception {
        list("usr_01", -1, 20)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("page"));
        list("usr_01", 0, 51)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("size"));
        list("usr_01", 0, 0).andExpect(jsonPath("$.error.field").value("size"));
    }

    @Test
    void receiptMatchesTheSampleUi() throws Exception {
        receipt("usr_01", "txn_5500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn_5500"))
                .andExpect(jsonPath("$.inquiryId").value("inq_seed_5500"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.failureCode").value(nullValue()))
                .andExpect(jsonPath("$.reference").value("MP-20260920-5500"))
                .andExpect(jsonPath("$.service.nameEn").value("Cairo Electricity"))
                .andExpect(jsonPath("$.service.category").value("electricity"))
                .andExpect(jsonPath("$.subscriberNumber").value("1024750891"))
                .andExpect(jsonPath("$.customerName").value("Mina A."))
                .andExpect(jsonPath("$.billMonth").value("2026-08"))
                .andExpect(jsonPath("$.amountDue").value(24750))
                .andExpect(jsonPath("$.serviceFee").value(500))
                .andExpect(jsonPath("$.vat").value(70))
                .andExpect(jsonPath("$.total").value(25320))
                .andExpect(jsonPath("$.currency").value("EGP"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-20T16:31:00Z"))
                .andExpect(jsonPath("$.paidAt").value("2026-09-20T16:31:00Z"));
    }

    @Test
    void anotherUsersOrUnknownReceiptIsNotFound() throws Exception {
        receipt("usr_02", "txn_5500")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRANSACTION_NOT_FOUND"));
        receipt("usr_01", "txn_0")
                .andExpect(jsonPath("$.error.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void duePendingPaymentIsReportedAsSuccessWhenRead() throws Exception {
        PaymentClient omar = new PaymentClient(mvc, "usr_03");
        String inquiryId = omar.openInquiry("svc_elec_cairo", "1024750898");
        String transactionId =
                JsonPath.read(
                        omar.confirm(inquiryId, UUID.randomUUID(), "9999")
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.transactionId");
        receipt("usr_03", transactionId).andExpect(jsonPath("$.status").value("PENDING"));

        jdbc.update(
                "UPDATE transactions SET pending_until = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-01-01T00:00:10Z")),
                transactionId);

        receipt("usr_03", transactionId)
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paidAt").value("2026-01-01T00:00:10Z"));
    }

    private ResultActions list(String userId, int page, int size) throws Exception {
        return mvc.perform(
                withClientHeaders(get("/v1/payments/transactions"))
                        .header(Headers.USER_ID, userId)
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size)));
    }

    private ResultActions receipt(String userId, String transactionId) throws Exception {
        return mvc.perform(
                withClientHeaders(get("/v1/payments/transactions/" + transactionId))
                        .header(Headers.USER_ID, userId));
    }
}
