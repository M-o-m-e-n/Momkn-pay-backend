package com.momknpay.user;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.web.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** GET/PATCH /v1/profile (FR-PRO-1…5, FR-COM-4…5). Each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProfileIT {

    @Autowired private MockMvc mvc;

    @Test
    void getReturnsTheSeededProfile() throws Exception {
        mvc.perform(withClientHeaders(get("/v1/profile")).header(Headers.USER_ID, "usr_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("usr_01"))
                .andExpect(jsonPath("$.fullName").value("Mina Adel"))
                .andExpect(jsonPath("$.mobile").value("01000000001"))
                .andExpect(jsonPath("$.email").value("mina@example.com"))
                .andExpect(jsonPath("$.memberSince").value("2026-09-01T09:00:00Z"))
                .andExpect(jsonPath("$.pinHash").doesNotExist());
    }

    @Test
    void unknownOrMissingUserIsRejected() throws Exception {
        mvc.perform(withClientHeaders(get("/v1/profile")).header(Headers.USER_ID, "usr_99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        mvc.perform(withClientHeaders(get("/v1/profile")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("X-User-Id"));
    }

    @Test
    void nameAndEmailAreUpdatedTrimmedAndLowerCased() throws Exception {
        patchProfile(
                        "usr_02",
                        "{\"fullName\":\"  Sara Hassan Ali  \",\"email\":\"Sara.New@Example.COM\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Sara Hassan Ali"))
                .andExpect(jsonPath("$.email").value("sara.new@example.com"))
                .andExpect(jsonPath("$.mobile").value("01000000002"));

        mvc.perform(withClientHeaders(get("/v1/profile")).header(Headers.USER_ID, "usr_02"))
                .andExpect(jsonPath("$.email").value("sara.new@example.com"));
    }

    @Test
    void eitherFieldAloneCanBeUpdated() throws Exception {
        patchProfile("usr_03", "{\"fullName\":\"Omar K.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Omar K."))
                .andExpect(jsonPath("$.email").value("omar@example.com"));
    }

    @Test
    void mobileIsReadOnly() throws Exception {
        patchProfile("usr_01", "{\"mobile\":\"01011111111\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("mobile"));
    }

    @Test
    void emptyBodyIsValidationError() throws Exception {
        patchProfile("usr_01", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value(nullValue()));
    }

    @Test
    void invalidFieldsNameTheField() throws Exception {
        patchProfile("usr_01", "{\"fullName\":\"   \"}")
                .andExpect(jsonPath("$.error.field").value("fullName"));
        patchProfile("usr_01", "{\"fullName\":\" a \"}")
                .andExpect(jsonPath("$.error.field").value("fullName"));
        patchProfile("usr_01", "{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("email"));
    }

    @Test
    void anotherUsersEmailIsAConflictWhateverItsCase() throws Exception {
        patchProfile("usr_02", "{\"email\":\"MINA@example.com\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_USED"))
                .andExpect(jsonPath("$.error.field").value("email"));
    }

    @Test
    void keepingYourOwnEmailIsNotAConflict() throws Exception {
        patchProfile("usr_01", "{\"email\":\"mina@example.com\"}").andExpect(status().isOk());
    }

    private ResultActions patchProfile(String userId, String json) throws Exception {
        return mvc.perform(
                withClientHeaders(patch("/v1/profile"))
                        .header(Headers.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }
}
