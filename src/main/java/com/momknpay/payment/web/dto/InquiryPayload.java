package com.momknpay.payment.web.dto;

import com.momknpay.common.util.Masking;
import com.momknpay.session.service.EncryptedPayload;

/** Decrypted plaintext of an inquiry: never sent in clear, never logged unmasked. */
public record InquiryPayload(String subscriberNumber, String nonce, Long ts)
        implements EncryptedPayload {

    @Override
    public String toString() {
        return "InquiryPayload[subscriberNumber=%s]"
                .formatted(Masking.subscriber(subscriberNumber));
    }
}
