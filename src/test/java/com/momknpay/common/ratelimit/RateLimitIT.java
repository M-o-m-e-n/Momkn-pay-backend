package com.momknpay.common.ratelimit;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.web.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 5 attempts per minute per user on POST /v1/sessions (FR-SES-6, NFR-SEC-9). */
@SpringBootTest(properties = "app.rate-limit.per-minute=5") // own context, real limit
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

    @Autowired private MockMvc mvc;

    @Test
    void sixthSessionWithinAMinuteIsRateLimitedWithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            createSession("usr_03").andExpect(status().isCreated());
        }

        createSession("usr_03")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(Headers.RETRY_AFTER))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

        createSession("usr_02").andExpect(status().isCreated()); // other users are unaffected
        mvc.perform(
                        withClientHeaders(delete("/v1/sessions/ses_unknown"))
                                .header(Headers.USER_ID, "usr_03"))
                .andExpect(status().isNotFound()); // revoke is not rate-limited
    }

    @Test
    void requestsWithBadHeadersDoNotConsumeTokens() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/v1/sessions").header(Headers.USER_ID, "usr_01"))
                    .andExpect(status().isBadRequest());
        }

        createSession("usr_01").andExpect(status().isCreated());
    }

    private ResultActions createSession(String userId) throws Exception {
        return mvc.perform(withClientHeaders(post("/v1/sessions")).header(Headers.USER_ID, userId));
    }
}
