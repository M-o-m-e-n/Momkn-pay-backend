package com.momknpay.common.error;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momknpay.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Every failure, expected or not, comes back as the error envelope (FR-COM-3, NFR-SEC-10). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ErrorEnvelopeIT {

    @Autowired private MockMvc mvc;

    @Test
    void apiExceptionUsesItsCodeAndStatus() throws Exception {
        expectError(
                        mvc.perform(withClientHeaders(get("/v1/test/errors/api"))),
                        410,
                        "INQUIRY_EXPIRED",
                        null)
                .andExpect(jsonPath("$.error.messageEn").value("This inquiry has expired."))
                .andExpect(jsonPath("$.error.messageAr").value("انتهت صلاحية الاستعلام."));
    }

    @Test
    void apiExceptionCarriesItsField() throws Exception {
        expectError(
                mvc.perform(withClientHeaders(get("/v1/test/errors/api-field"))),
                409,
                "EMAIL_ALREADY_USED",
                "email");
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutDetails() throws Exception {
        expectError(
                        mvc.perform(withClientHeaders(get("/v1/test/errors/boom"))),
                        500,
                        "INTERNAL_ERROR",
                        null)
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        expectError(mvc.perform(get("/no/such/route")), 404, "NOT_FOUND", null);
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        expectError(
                mvc.perform(withClientHeaders(delete("/v1/test/errors/api"))),
                405,
                "METHOD_NOT_ALLOWED",
                null);
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        expectError(postJson("{not json"), 400, "VALIDATION_ERROR", null);
    }

    @Test
    void unknownPropertyIsNamedInField() throws Exception {
        expectError(
                postJson("{\"name\":\"ok\",\"mobile\":\"01011111111\"}"),
                400,
                "VALIDATION_ERROR",
                "mobile");
    }

    @Test
    void beanValidationNamesTheField() throws Exception {
        expectError(postJson("{\"name\":\"far too long\"}"), 400, "VALIDATION_ERROR", "name");
    }

    @Test
    void missingHeaderIsNamedInField() throws Exception {
        expectError(
                mvc.perform(withClientHeaders(get("/v1/test/errors/header"))),
                400,
                "VALIDATION_ERROR",
                "X-Session-Id");
    }

    @Test
    void invalidQueryParameterIsNamedInField() throws Exception {
        expectError(
                mvc.perform(withClientHeaders(get("/v1/test/errors/param")).param("size", "51")),
                400,
                "VALIDATION_ERROR",
                "size");
        expectError(
                mvc.perform(withClientHeaders(get("/v1/test/errors/param")).param("size", "abc")),
                400,
                "VALIDATION_ERROR",
                "size");
    }

    private ResultActions postJson(String json) throws Exception {
        return mvc.perform(
                withClientHeaders(post("/v1/test/errors/body"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private static ResultActions expectError(
            ResultActions result, int status, String code, String field) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.messageEn").isString())
                .andExpect(jsonPath("$.error.messageAr").isString());
        if (field == null) {
            result.andExpect(jsonPath("$.error.field").value(nullValue()));
        } else {
            result.andExpect(jsonPath("$.error.field").value(field));
        }
        return result;
    }
}
