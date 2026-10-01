package com.momknpay.payment.web.dto;

import com.momknpay.payload.service.EncryptedPayload;

/** Decrypted plaintext of a confirmation. The PIN is never stored, logged or printed. */
public record ConfirmPayload(String pin, String nonce, Long ts) implements EncryptedPayload {

    @Override
    public String toString() {
        return "ConfirmPayload[pin=****]";
    }
}
