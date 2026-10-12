package com.momknpay.session.web.dto;

import java.time.Instant;

/**
 * Response of {@code POST /v1/sessions}. It carries no key: the client derives the session key from
 * its own private key, the server's long-term public key and {@code serverPublicKey} (ADR-013).
 *
 * @param serverPublicKey the server's ephemeral P-256 public key for this session: base64 of its
 *     X.509 SubjectPublicKeyInfo encoding
 */
public record CreateSessionResponse(String sessionId, String serverPublicKey, Instant expiresAt) {}
