package com.momknpay.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momknpay.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Required client headers on /v1, request-id echo and logging (FR-COM-1…2). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class HeadersIT {

    private static final String REQUEST_ID = "3f6c1d2e-8a4b-4c1e-9f0a-2b7d5e6c8a91";

    @Autowired private MockMvc mvc;

    @Test
    void validHeadersReachTheControllerAndRequestIdIsEchoed() throws Exception {
        mvc.perform(ping(REQUEST_ID, "ios", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(header().string(Headers.REQUEST_ID, REQUEST_ID));
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(
            nullValues = "NULL",
            value = {
                "NULL,                                 ios,     1.0.0,  X-Request-Id",
                "not-a-uuid,                           ios,     1.0.0,  X-Request-Id",
                "3f6c1d2e-8a4b-4c1e-9f0a-2b7d5e6c8a91, NULL,    1.0.0,  X-Client-Platform",
                "3f6c1d2e-8a4b-4c1e-9f0a-2b7d5e6c8a91, web,     1.0.0,  X-Client-Platform",
                "3f6c1d2e-8a4b-4c1e-9f0a-2b7d5e6c8a91, android, NULL,   X-Client-Version",
                "3f6c1d2e-8a4b-4c1e-9f0a-2b7d5e6c8a91, android, 123456789012345678901234567890123,"
                        + " X-Client-Version",
            })
    void missingOrInvalidHeaderIsValidationError(
            String requestId, String platform, String version, String field) throws Exception {
        mvc.perform(ping(requestId, platform, version))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value(field));
    }

    @Test
    void docsAndHealthDoNotNeedClientHeaders() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void everyLogLineOfTheRequestCarriesTheRequestId(CapturedOutput output) throws Exception {
        mvc.perform(ping(REQUEST_ID, "android", "2.3.1")).andExpect(status().isOk());

        assertThat(output.getOut())
                .contains("[" + REQUEST_ID + "] c.m.common.web.PingProbeController - probe.ping")
                .contains("[" + REQUEST_ID + "] access - method=GET path=/v1/test/ping status=200");
    }

    private static MockHttpServletRequestBuilder ping(
            String requestId, String platform, String version) {
        MockHttpServletRequestBuilder builder = get("/v1/test/ping");
        if (requestId != null) {
            builder.header(Headers.REQUEST_ID, requestId);
        }
        if (platform != null) {
            builder.header(Headers.CLIENT_PLATFORM, platform);
        }
        if (version != null) {
            builder.header(Headers.CLIENT_VERSION, version);
        }
        return builder;
    }
}
