package com.momknpay.payment.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.Headers;
import com.momknpay.common.web.UserRef;
import com.momknpay.payment.service.ConfirmService;
import com.momknpay.payment.service.InquiryService;
import com.momknpay.payment.web.dto.ConfirmRequest;
import com.momknpay.payment.web.dto.ConfirmResponse;
import com.momknpay.payment.web.dto.InquiryRequest;
import com.momknpay.payment.web.dto.InquiryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments", description = "Fees inquiry and idempotent payment confirmation")
@RequestMapping("/payments")
class PaymentController {

    private final InquiryService inquiryService;
    private final ConfirmService confirmService;

    PaymentController(InquiryService inquiryService, ConfirmService confirmService) {
        this.inquiryService = inquiryService;
        this.confirmService = confirmService;
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
            description = "SUBSCRIBER_NOT_FOUND, SERVICE_NOT_FOUND or USER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "BILL_ALREADY_PAID")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE — inactive service")
    @PostMapping("/inquiry")
    InquiryResponse inquiry(@CurrentUser UserRef user, @Valid @RequestBody InquiryRequest request) {
        return inquiryService.inquire(user.id(), request);
    }

    @Operation(
            summary = "Pay an open inquiry with the PIN (encrypted, idempotent)",
            description =
                    "payload decrypts to {pin, nonce, ts}. Idempotency-Key is checked before"
                            + " decrypting: repeating it returns the same transaction (current"
                            + " status) and never creates a second one, even when the exact same"
                            + " bytes are resent. Wrong PIN → VALIDATION_ERROR on pin; the third"
                            + " invalidates the inquiry. Rate-limited to 5 per minute per user.")
    @ApiResponse(responseCode = "200", description = "SUCCESS or PENDING — first call and replays")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR (incl. pin, Idempotency-Key) or DECRYPTION_FAILED")
    @ApiResponse(responseCode = "402", description = "INSUFFICIENT_BALANCE — simulated decline")
    @ApiResponse(responseCode = "404", description = "INQUIRY_NOT_FOUND or USER_NOT_FOUND")
    @ApiResponse(
            responseCode = "409",
            description = "IDEMPOTENCY_CONFLICT or INQUIRY_ALREADY_CONFIRMED")
    @ApiResponse(responseCode = "410", description = "INQUIRY_EXPIRED or INQUIRY_INVALIDATED")
    @ApiResponse(responseCode = "422", description = "AMOUNT_OUT_OF_RANGE")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED — see Retry-After")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE — inactive service")
    @PostMapping("/confirm")
    ConfirmResponse confirm(
            @CurrentUser UserRef user,
            @RequestHeader(Headers.IDEMPOTENCY_KEY) UUID idempotencyKey,
            @Valid @RequestBody ConfirmRequest request) {
        return confirmService.confirm(user.id(), idempotencyKey, request);
    }
}
