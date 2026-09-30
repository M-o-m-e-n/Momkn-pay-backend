package com.momknpay.payment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** U6–U7: the fee formula, rounding up in integer piastres (SRS §6.3, LLD §9.1). */
class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @Test
    void workedExampleFromTheBrief() {
        assertThat(calculator.calculate(24_750)).isEqualTo(new Fees(24_750, 500, 70, 25_320));
    }

    @ParameterizedTest(name = "amountDue {0} → fee {1}, vat {2}, total {3}")
    @CsvSource({
        "24750,  500, 70,  25320", // minimum fee applies
        "99999,  500, 70,  100569", // ceil(499.995) = 500
        "100000, 500, 70,  100570",
        "100001, 501, 71,  100573", // ceil(500.005) = 501, ceil(70.14) = 71
        "150000, 750, 105, 150855", // percentage fee applies
        "510000, 2550, 357, 512907", // rule 6 for electricity: max 500000 + 10000
        "0,      500, 70,  570",
    })
    void feesRoundUpInPiastres(long amountDue, long fee, long vat, long total) {
        Fees fees = calculator.calculate(amountDue);

        assertThat(fees.serviceFee()).isEqualTo(fee);
        assertThat(fees.vat()).isEqualTo(vat);
        assertThat(fees.total()).isEqualTo(total).isEqualTo(amountDue + fee + vat);
    }

    @Test
    void negativeAmountIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
