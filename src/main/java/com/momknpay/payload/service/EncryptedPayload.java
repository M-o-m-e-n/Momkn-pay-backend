package com.momknpay.payload.service;

/**
 * Plaintext of an encrypted request payload. Every payload carries a single-use {@code nonce} (32
 * hex characters) and its creation time {@code ts} in unix seconds.
 *
 * <p>Implementations hold sensitive values and must mask them in {@code toString()}.
 */
public interface EncryptedPayload {

    String nonce();

    Long ts();
}
