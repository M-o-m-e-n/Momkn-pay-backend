package com.momknpay.payment.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.Headers;
import com.momknpay.common.web.UserRef;
import com.momknpay.payment.service.InquiryService;
import com.momknpay.payment.web.dto.InquiryRequest;
import com.momknpay.payment.web.dto.InquiryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Payments", description = "Fees inquiry and idempotent payment confirmation")
@RequestMapping("/payments")
class PaymentController {

    private final InquiryService inquiryService;

    PaymentController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @Operation(
            summary = "Fees inquiry for a subscriber (encrypted subscriber number)",
            description =
                    "payload decrypts to {subscriberNumber, nonce, ts}. The quote is valid for 5"
                            + " minutes. Outcome by the number's last digit: 0 not found, 1–5"
                            + " normal, 6 above maxAmount, 7 declines on confirm, 8 pending on"
                            + " confirm, 9 already paid. Services ending in _slow answer after"
                            + " 8 s.")
    @ApiResponse(responseCode = "200", description = "Quote")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR (incl. subscriberNumber) or DECRYPTION_FAILED")
    @ApiResponse(
            responseCode = "404",
            description =
                    "SUBSCRIBER_NOT_FOUND, SERVICE_NOT_FOUND, SESSION_NOT_FOUND or USER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "BILL_ALREADY_PAID")
    @ApiResponse(responseCode = "410", description = "SESSION_EXPIRED")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE — inactive service")
    @PostMapping("/inquiry")
    InquiryResponse inquiry(
            @CurrentUser UserRef user,
            @RequestHeader(Headers.SESSION_ID) @Size(max = 40) String sessionId,
            @Valid @RequestBody InquiryRequest request) {
        return inquiryService.inquire(user.id(), sessionId, request);
    }
}
