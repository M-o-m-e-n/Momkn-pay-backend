package com.momknpay.user.web;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** {@code X-User-Id} → seeded user, end to end (FR-COM-4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CurrentUserIT {

    @Autowired private MockMvc mvc;

    @Test
    void seededUserIsResolved() throws Exception {
        mvc.perform(withClientHeaders(get("/v1/test/whoami")).header(Headers.USER_ID, "usr_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("usr_01"));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        mvc.perform(withClientHeaders(get("/v1/test/whoami")).header(Headers.USER_ID, "usr_99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void missingUserHeaderIsValidationError() throws Exception {
        mvc.perform(withClientHeaders(get("/v1/test/whoami")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("X-User-Id"));
    }
}
