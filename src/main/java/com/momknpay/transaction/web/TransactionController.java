package com.momknpay.transaction.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.UserRef;
import com.momknpay.transaction.service.TransactionService;
import com.momknpay.transaction.web.dto.PageResponse;
import com.momknpay.transaction.web.dto.ReceiptResponse;
import com.momknpay.transaction.web.dto.TransactionItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Transactions", description = "Payment history and receipts")
@RequestMapping("/payments/transactions")
class TransactionController {

    private final TransactionService transactionService;

    TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(
            summary = "The user's transactions, newest first",
            description = "PENDING rows whose 10 s have passed are reported as SUCCESS.")
    @ApiResponse(responseCode = "200", description = "One page")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — page ≥ 0, size 1–50")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @GetMapping
    PageResponse<TransactionItem> list(
            @CurrentUser UserRef user,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(50) int size) {
        return transactionService.list(user.id(), page, size);
    }

    @Operation(summary = "Full receipt of one transaction")
    @ApiResponse(responseCode = "200", description = "Receipt")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @ApiResponse(
            responseCode = "404",
            description = "TRANSACTION_NOT_FOUND (also for another user's) or USER_NOT_FOUND")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @GetMapping("/{id}")
    ReceiptResponse receipt(
            @CurrentUser UserRef user, @PathVariable("id") @Size(max = 32) String transactionId) {
        return transactionService.receipt(user.id(), transactionId);
    }
}
