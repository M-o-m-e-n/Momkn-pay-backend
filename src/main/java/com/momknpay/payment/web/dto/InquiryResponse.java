package com.momknpay.payment.web.dto;

import com.momknpay.payment.domain.Inquiry;
import java.time.Instant;

/** The quote: every figure computed on the server in piastres; clients only format. */
public record InquiryResponse(
        String inquiryId,
        String serviceId,
        String customerName,
        String billMonth,
        long amountDue,
        long serviceFee,
        long vat,
        long total,
        String currency,
        Instant expiresAt) {

    public static final String CURRENCY = "EGP";

    public static InquiryResponse from(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getService().getId(),
                inquiry.getCustomerName(),
                inquiry.getBillMonth(),
                inquiry.getAmountDue(),
                inquiry.getServiceFee(),
                inquiry.getVat(),
                inquiry.getTotal(),
                CURRENCY,
                inquiry.getExpiresAt());
    }
}
