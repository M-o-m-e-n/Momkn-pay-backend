package com.momknpay.payment.web.dto;

import com.momknpay.session.service.EncryptedPayload;

/** Decrypted plaintext of a confirmation. The CVV is never stored, logged or printed. */
public record ConfirmPayload(String cvv, String nonce, Long ts) implements EncryptedPayload {

    @Override
    public String toString() {
        return "ConfirmPayload[cvv=****]";
    }
}
