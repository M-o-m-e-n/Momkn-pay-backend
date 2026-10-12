package com.momknpay.support;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.common.web.Headers;
import com.momknpay.session.service.SessionService;
import com.momknpay.session.web.dto.CreateSessionResponse;
import java.security.KeyPair;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Opens crypto sessions the way a client does: fresh key pair, handshake, derived key. */
public final class TestSessions {

    private TestSessions() {}

    /** Directly through the service, for tests that are not about the sessions endpoint. */
    public static TestSession open(SessionService sessionService, String userId) {
        KeyPair client = TestCrypto.clientKeyPair();
        CreateSessionResponse response =
                sessionService.create(userId, TestCrypto.spki(client.getPublic()));
        return derive(
                client,
                response.sessionId(),
                response.serverPublicKey(),
                response.expiresAt(),
                userId);
    }

    /** Over HTTP, like a real client. */
    public static TestSession open(MockMvc mvc, String userId) throws Exception {
        KeyPair client = TestCrypto.clientKeyPair();
        String body =
                mvc.perform(createRequest(userId, TestCrypto.spki(client.getPublic())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return derive(
                client,
                JsonPath.read(body, "$.sessionId"),
                JsonPath.read(body, "$.serverPublicKey"),
                Instant.parse(JsonPath.read(body, "$.expiresAt")),
                userId);
    }

    /** {@code POST /v1/sessions} with the client headers, the user and this public key. */
    public static MockHttpServletRequestBuilder createRequest(
            String userId, String clientPublicKey) {
        return withClientHeaders(post("/v1/sessions"))
                .header(Headers.USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"clientPublicKey":"%s"}\
                        """
                                .formatted(clientPublicKey));
    }

    private static TestSession derive(
            KeyPair client,
            String sessionId,
            String serverEphemeralKey,
            Instant expiresAt,
            String userId) {
        String sessionKey =
                TestCrypto.deriveSessionKey(
                        client,
                        TestCrypto.SERVER_PUBLIC_KEY,
                        serverEphemeralKey,
                        sessionId,
                        userId);
        return new TestSession(sessionId, sessionKey, expiresAt);
    }
}
