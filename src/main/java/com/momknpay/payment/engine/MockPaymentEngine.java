package com.momknpay.payment.engine;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.payment.domain.MockRule;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Decides every outcome from the last digit of the subscriber number — deterministic, no
 * randomness, no I/O, so all three teams can reproduce any case (SRS §6.1):
 *
 * <pre>
 * 0   → SUBSCRIBER_NOT_FOUND
 * 1–5 → normal bill: amountDue = digits 3–7 (at least minAmount)
 * 6   → bill above maxAmount (confirm is rejected)
 * 7   → normal bill, confirm declines (INSUFFICIENT_BALANCE)
 * 8   → normal bill, confirm is PENDING then SUCCESS
 * 9   → BILL_ALREADY_PAID
 * </pre>
 *
 * The caller has already validated the number against the service's {@code inputPattern} (every
 * pattern requires at least 8 digits).
 */
@Component
public class MockPaymentEngine {

    /** How far above {@code maxAmount} a rule-6 bill is. */
    static final long LARGE_BILL_EXCESS = 10_000;

    private final FeeCalculator feeCalculator;

    public MockPaymentEngine(FeeCalculator feeCalculator) {
        this.feeCalculator = feeCalculator;
    }

    /**
     * @throws ApiException SUBSCRIBER_NOT_FOUND (last digit 0) or BILL_ALREADY_PAID (last digit 9)
     */
    public InquiryDecision evaluateInquiry(
            BillerService service, String subscriberNumber, Instant now) {
        int lastDigit = subscriberNumber.charAt(subscriberNumber.length() - 1) - '0';
        return switch (lastDigit) {
            case 0 -> throw new ApiException(ErrorCode.SUBSCRIBER_NOT_FOUND);
            case 9 -> throw new ApiException(ErrorCode.BILL_ALREADY_PAID);
            case 6 ->
                    decide(
                            subscriberNumber,
                            now,
                            Math.addExact(service.getMaxAmount(), LARGE_BILL_EXCESS),
                            MockRule.LARGE);
            case 7 ->
                    decide(
                            subscriberNumber,
                            now,
                            normalAmount(service, subscriberNumber),
                            MockRule.DECLINE);
            case 8 ->
                    decide(
                            subscriberNumber,
                            now,
                            normalAmount(service, subscriberNumber),
                            MockRule.PENDING);
            default ->
                    decide(
                            subscriberNumber,
                            now,
                            normalAmount(service, subscriberNumber),
                            MockRule.NORMAL);
        };
    }

    /** Digits at 1-based positions 3–7, raised to the service minimum ("00000" → minAmount). */
    static long normalAmount(BillerService service, String subscriberNumber) {
        long digits = Long.parseLong(subscriberNumber.substring(2, 7));
        return Math.max(digits, service.getMinAmount());
    }

    private InquiryDecision decide(
            String subscriberNumber, Instant now, long amountDue, MockRule rule) {
        return new InquiryDecision(
                rule,
                feeCalculator.calculate(amountDue),
                CustomerNames.forSubscriber(subscriberNumber),
                billMonth(now));
    }

    /** The calendar month before the inquiry, in UTC ({@code YYYY-MM}). */
    static String billMonth(Instant now) {
        return YearMonth.from(now.atZone(ZoneOffset.UTC)).minusMonths(1).toString();
    }
}
