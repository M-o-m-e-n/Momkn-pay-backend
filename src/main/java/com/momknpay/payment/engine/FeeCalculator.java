package com.momknpay.payment.engine;

import org.springframework.stereotype.Component;

/**
 * The brief's fee formula in integer piastres, always rounding up (SRS §6.3):
 *
 * <pre>
 * serviceFee = max(500, ceil(0.005 × amountDue))
 * vat        = ceil(0.14 × serviceFee)
 * total      = amountDue + serviceFee + vat
 * </pre>
 *
 * Integer arithmetic only — no floating point anywhere near money.
 */
@Component
public class FeeCalculator {

    static final long MIN_SERVICE_FEE = 500;

    public Fees calculate(long amountDue) {
        if (amountDue < 0) {
            throw new IllegalArgumentException("amountDue must not be negative");
        }
        long percentageFee = Math.ceilDiv(amountDue, 200); // ceil(amountDue × 0.5 %)
        long serviceFee = Math.max(MIN_SERVICE_FEE, percentageFee);
        long vat = Math.ceilDiv(Math.multiplyExact(serviceFee, 14), 100); // ceil(fee × 14 %)
        long total = Math.addExact(Math.addExact(amountDue, serviceFee), vat);
        return new Fees(amountDue, serviceFee, vat, total);
    }
}
