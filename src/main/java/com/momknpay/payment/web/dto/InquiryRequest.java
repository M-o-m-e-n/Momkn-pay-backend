package com.momknpay.payment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param payload base64(iv ‖ AES-GCM ciphertext ‖ tag) of {@link InquiryPayload}
 */
public record InquiryRequest(
        @NotBlank @Size(max = 64) String serviceId, @NotBlank @Size(max = 4096) String payload) {

    @Override
    public String toString() {
        return "InquiryRequest[serviceId=%s, payload=****]".formatted(serviceId);
    }
}
