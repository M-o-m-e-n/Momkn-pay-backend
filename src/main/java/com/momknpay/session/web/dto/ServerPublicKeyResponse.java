package com.momknpay.session.web.dto;

/**
 * Response of {@code GET /v1/sessions/public-key}: the server's long-term handshake key. Not a
 * secret; clients should still use the copy built into the app, so a proxy cannot replace it.
 *
 * @param keyId short fingerprint of the key
 * @param algorithm always {@code ECDH-P256}
 * @param publicKey base64 of the X.509 SubjectPublicKeyInfo encoding
 */
public record ServerPublicKeyResponse(String keyId, String algorithm, String publicKey) {}
