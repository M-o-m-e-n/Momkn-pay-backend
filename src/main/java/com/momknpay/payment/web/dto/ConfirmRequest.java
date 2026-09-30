package com.momknpay.payment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param payload base64(iv ‖ AES-GCM ciphertext ‖ tag) of {@link ConfirmPayload}
 */
public record ConfirmRequest(
        @NotBlank @Size(max = 32) String inquiryId, @NotBlank @Size(max = 4096) String payload) {

    @Override
    public String toString() {
        return "ConfirmRequest[inquiryId=%s, payload=****]".formatted(inquiryId);
    }
}
