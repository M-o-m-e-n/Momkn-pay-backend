package com.momknpay.support;

import java.time.Instant;

/**
 * A crypto session as a client holds it after the handshake.
 *
 * @param sessionKey base64 of the 32-byte key the client derived (it never came from the server)
 */
public record TestSession(String sessionId, String sessionKey, Instant expiresAt) {}
