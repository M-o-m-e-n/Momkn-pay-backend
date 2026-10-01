package com.momknpay.common.ratelimit;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.web.Headers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 5 attempts per minute per user on POST /v1/payments/confirm (FR-PAY-11, NFR-SEC-9). */
@SpringBootTest(properties = "app.rate-limit.per-minute=5") // own context, real limit
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

    private static final String BODY = "{\"inquiryId\":\"inq_none\",\"payload\":\"AAAA\"}";

    @Autowired private MockMvc mvc;

    @Test
    void sixthConfirmWithinAMinuteIsRateLimitedWhateverItsOutcome() throws Exception {
        for (int i = 0; i < 5; i++) {
            confirm("usr_02").andExpect(status().isNotFound()); // unknown inquiry, still counted
        }

        confirm("usr_02")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(Headers.RETRY_AFTER))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

        confirm("usr_03").andExpect(status().isNotFound()); // other users are unaffected
        mvc.perform(withClientHeaders(get("/v1/profile")).header(Headers.USER_ID, "usr_02"))
                .andExpect(status().isOk()); // other endpoints are not limited
    }

    @Test
    void requestsWithBadHeadersDoNotConsumeTokens() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(
                            post("/v1/payments/confirm") // no client headers
                                    .header(Headers.USER_ID, "usr_01")
                                    .header(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(BODY))
                    .andExpect(status().isBadRequest());
        }

        confirm("usr_01").andExpect(status().isNotFound()); // still has all its attempts
    }

    private ResultActions confirm(String userId) throws Exception {
        return mvc.perform(
                withClientHeaders(post("/v1/payments/confirm"))
                        .header(Headers.USER_ID, userId)
                        .header(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY));
    }
}
