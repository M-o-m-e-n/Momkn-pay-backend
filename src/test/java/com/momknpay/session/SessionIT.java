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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** POST/DELETE /v1/sessions (FR-SES-1…5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SessionIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private KeyWrapper keyWrapper;

    @Test
    void createReturnsA32ByteKeyValidFor30Minutes() throws Exception {
        MvcResult result =
                mvc.perform(
                                withClientHeaders(post("/v1/sessions"))
                                        .header(Headers.USER_ID, "usr_01"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.sessionId").value(startsWith("ses_")))
                        .andReturn();

        String body = result.getResponse().getContentAsString();
        byte[] key = Base64.getDecoder().decode((String) JsonPath.read(body, "$.sessionKey"));
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));

        assertThat(key).hasSize(32);
        assertThat(Duration.between(Instant.now(), expiresAt))
                .isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
    }

    @Test
    void keyIsStoredOnlyWrappedAndNeverLogged(CapturedOutput output) throws Exception {
        String body = createSession("usr_02");
        String sessionId = JsonPath.read(body, "$.sessionId");
        String sessionKey = JsonPath.read(body, "$.sessionKey");

        byte[] stored =
                jdbc.queryForObject(
                        "SELECT wrapped_key FROM sessions WHERE id = ?", byte[].class, sessionId);

        assertThat(stored).isNotEqualTo(Base64.getDecoder().decode(sessionKey));
        assertThat(Base64.getEncoder().encodeToString(keyWrapper.unwrap(sessionId, stored)))
                .isEqualTo(sessionKey);
        assertThat(output.getAll()).contains(sessionId).doesNotContain(sessionKey);
    }

    @Test
    void theKeyCannotBeReadBack() throws Exception {
        String sessionId = JsonPath.read(createSession("usr_01"), "$.sessionId");

        mvc.perform(
                        withClientHeaders(get("/v1/sessions/" + sessionId))
                                .header(Headers.USER_ID, "usr_01"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void revokeIsIdempotent() throws Exception {
        String sessionId = JsonPath.read(createSession("usr_01"), "$.sessionId");

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
        String sessionId = JsonPath.read(createSession("usr_01"), "$.sessionId");

        revoke("usr_02", sessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        revoke("usr_01", "ses_unknown")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void unknownUserCannotCreateASession() throws Exception {
        mvc.perform(withClientHeaders(post("/v1/sessions")).header(Headers.USER_ID, "usr_99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    private String createSession(String userId) throws Exception {
        return mvc.perform(withClientHeaders(post("/v1/sessions")).header(Headers.USER_ID, userId))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private ResultActions revoke(String userId, String sessionId) throws Exception {
        return mvc.perform(
                withClientHeaders(delete("/v1/sessions/" + sessionId))
                        .header(Headers.USER_ID, userId));
    }
}
