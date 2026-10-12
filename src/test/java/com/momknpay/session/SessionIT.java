package com.momknpay.session;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.crypto.KeyWrapper;
import com.momknpay.common.web.Headers;
import com.momknpay.support.PaymentClient;
import com.momknpay.support.TestCrypto;
import com.momknpay.support.TestSession;
import com.momknpay.support.TestSessions;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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

/** GET /v1/sessions/public-key, POST and DELETE /v1/sessions (FR-SES-1…6, ADR-013). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SessionIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KeyWrapper keyWrapper;

    @Test
    void publicKeyEndpointReturnsTheServersHandshakeKeyWithoutAUser() throws Exception {
        mvc.perform(withClientHeaders(get("/v1/sessions/public-key")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("ECDH-P256"))
                .andExpect(jsonPath("$.keyId").isNotEmpty())
                .andExpect(jsonPath("$.publicKey").value(TestCrypto.SERVER_PUBLIC_KEY));
    }

    @Test
    void createReturnsNoKeyOnlyTheServersEphemeralPublicKey() throws Exception {
        KeyPair client = TestCrypto.clientKeyPair();

        String body =
                mvc.perform(
                                TestSessions.createRequest(
                                        "usr_01", TestCrypto.spki(client.getPublic())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.sessionId").value(startsWith("ses_")))
                        .andExpect(jsonPath("$.serverPublicKey").isNotEmpty())
                        .andExpect(jsonPath("$.sessionKey").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        String ephemeral = JsonPath.read(body, "$.serverPublicKey");
        assertThat(Duration.between(Instant.now(), expiresAt))
                .isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
        assertThat(ephemeral).isNotEqualTo(TestCrypto.SERVER_PUBLIC_KEY);
    }

    @Test
    void clientDerivesTheKeyTheServerStoredAndItIsNeverLogged(CapturedOutput output)
            throws Exception {
        TestSession session = TestSessions.open(mvc, "usr_02");

        byte[] stored =
                jdbc.queryForObject(
                        "SELECT wrapped_key FROM sessions WHERE id = ?",
                        byte[].class,
                        session.sessionId());

        assertThat(stored).isNotEqualTo(Base64.getDecoder().decode(session.sessionKey()));
        assertThat(
                        Base64.getEncoder()
                                .encodeToString(keyWrapper.unwrap(session.sessionId(), stored)))
                .isEqualTo(session.sessionKey());
        assertThat(output.getAll())
                .contains(session.sessionId())
                .doesNotContain(session.sessionKey());
    }

    @Test
    void aPaymentInquiryWorksWithTheDerivedKey() throws Exception {
        PaymentClient mina = new PaymentClient(mvc, "usr_01", TestSessions.open(mvc, "usr_01"));

        assertThat(mina.openInquiry("svc_elec_cairo", "1024750891")).startsWith("inq_");
    }

    @Test
    void twoSessionsWithTheSameClientKeyGetDifferentKeys() throws Exception {
        String clientKey = TestCrypto.spki(TestCrypto.clientKeyPair().getPublic());

        String first = serverPublicKeyOf(TestSessions.createRequest("usr_01", clientKey));
        String second = serverPublicKeyOf(TestSessions.createRequest("usr_01", clientKey));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aMissingOrInvalidClientKeyIsAValidationError() throws Exception {
        mvc.perform(
                        withClientHeaders(post("/v1/sessions"))
                                .header(Headers.USER_ID, "usr_01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("clientPublicKey"));
        for (String invalid : new String[] {"not-base64-!", "anVuaw==", "A".repeat(300)}) {
            mvc.perform(TestSessions.createRequest("usr_01", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("clientPublicKey"));
        }
    }

    @Test
    void theKeyCannotBeReadBack() throws Exception {
        String sessionId = TestSessions.open(mvc, "usr_01").sessionId();

        mvc.perform(
                        withClientHeaders(get("/v1/sessions/" + sessionId))
                                .header(Headers.USER_ID, "usr_01"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void revokeIsIdempotent() throws Exception {
        String sessionId = TestSessions.open(mvc, "usr_01").sessionId();

        revoke("usr_01", sessionId).andExpect(status().isNoContent());
        revoke("usr_01", sessionId).andExpect(status().isNoContent());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT revoked_at IS NOT NULL FROM sessions WHERE id = ?",
                                Boolean.class,
                                sessionId))
                .isTrue();
    }

    @Test
    void anotherUsersSessionIsNotFound() throws Exception {
        String sessionId = TestSessions.open(mvc, "usr_01").sessionId();

        revoke("usr_02", sessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        revoke("usr_01", "ses_unknown")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void unknownUserCannotCreateASession() throws Exception {
        String clientKey = TestCrypto.spki(TestCrypto.clientKeyPair().getPublic());

        mvc.perform(TestSessions.createRequest("usr_99", clientKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    private String serverPublicKeyOf(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body =
                mvc.perform(request)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(body, "$.serverPublicKey");
    }

    private ResultActions revoke(String userId, String sessionId) throws Exception {
        return mvc.perform(
                withClientHeaders(delete("/v1/sessions/" + sessionId))
                        .header(Headers.USER_ID, userId));
    }
}
