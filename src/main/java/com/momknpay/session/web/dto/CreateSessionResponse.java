package com.momknpay.session.web.dto;

import java.time.Instant;

/**
 * Response of {@code POST /v1/sessions}. The only place the session key ever leaves the server.
 *
 * @param sessionKey base64 of the 32-byte AES key — masked in {@link #toString()}
 */
public record CreateSessionResponse(String sessionId, String sessionKey, Instant expiresAt) {

    @Override
    public String toString() {
        return "CreateSessionResponse[sessionId=%s, sessionKey=****, expiresAt=%s]"
                .formatted(sessionId, expiresAt);
    }
}
