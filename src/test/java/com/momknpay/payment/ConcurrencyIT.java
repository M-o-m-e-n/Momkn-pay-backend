package com.momknpay.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import com.momknpay.support.Payloads;
import com.momknpay.support.PaymentClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Idempotency under concurrency (FR-PAY-4, NFR-REL-1…2): a client that fires the same request many
 * times at once — e.g. a retry storm after a timeout — pays once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConcurrencyIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void tenParallelRetriesOfOneRequestCreateOneTransaction() throws Exception {
        PaymentClient mina = new PaymentClient(mvc, "usr_01");
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750891");
        UUID key = UUID.randomUUID();
        String payload = Payloads.confirm("1234"); // identical bytes

        List<MockHttpServletResponse> responses =
                runTogether(10, () -> mina.confirmWithPayload(inquiryId, key, payload));

        List<String> ids = new ArrayList<>();
        for (MockHttpServletResponse response : responses) {
            assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
            ids.add(JsonPath.read(response.getContentAsString(), "$.transactionId"));
        }
        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(countTransactions(inquiryId)).isEqualTo(1);
    }

    @Test
    void twoDifferentKeysOnOneInquiryPayOnce() throws Exception {
        PaymentClient mina = new PaymentClient(mvc, "usr_01");
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750892");

        List<MockHttpServletResponse> responses =
                runTogether(2, () -> mina.confirm(inquiryId, UUID.randomUUID(), "1234"));

        List<Integer> statuses =
                responses.stream().map(MockHttpServletResponse::getStatus).toList();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        MockHttpServletResponse conflict =
                responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
        assertThat((String) JsonPath.read(conflict.getContentAsString(), "$.error.code"))
                .isEqualTo("INQUIRY_ALREADY_CONFIRMED");
        assertThat(countTransactions(inquiryId)).isEqualTo(1);
    }

    private int countTransactions(String inquiryId) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT count(*) AS n FROM transactions WHERE inquiry_id = ?", inquiryId);
        return ((Number) row.get("n")).intValue();
    }

    private interface Call {
        ResultActions run() throws Exception;
    }

    /** Starts every call at the same instant and waits for all of them. */
    private static List<MockHttpServletResponse> runTogether(int count, Call call)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<MockHttpServletResponse> task =
                        () -> {
                            start.await();
                            return call.run().andReturn().getResponse();
                        };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }
}
