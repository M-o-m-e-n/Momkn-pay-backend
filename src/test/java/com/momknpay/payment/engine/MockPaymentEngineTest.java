package com.momknpay.payment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.payment.domain.MockRule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** U8–U9: every last-digit rule of SRS §6.1, deterministic. */
class MockPaymentEngineTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    private final MockPaymentEngine engine = new MockPaymentEngine(new FeeCalculator());
    private final BillerService cairoElectricity = service(500, 500_000);

    @Test
    void workedExampleIsTheHappyPath() {
        InquiryDecision decision = engine.evaluateInquiry(cairoElectricity, "1024750891", NOW);

        assertThat(decision.rule()).isEqualTo(MockRule.NORMAL);
        assertThat(decision.fees()).isEqualTo(new Fees(24_750, 500, 70, 25_320));
        assertThat(decision.customerName()).isEqualTo("Mina A.");
        assertThat(decision.billMonth()).isEqualTo("2026-08");
    }

    @ParameterizedTest(name = "last digit {1} → {2}")
    @CsvSource({
        "1024750891, 1, NORMAL,  24750",
        "1024750892, 2, NORMAL,  24750",
        "1024750893, 3, NORMAL,  24750",
        "1024750894, 4, NORMAL,  24750",
        "1024750895, 5, NORMAL,  24750",
        "1024750896, 6, LARGE,   510000",
        "1024750897, 7, DECLINE, 24750",
        "1024750898, 8, PENDING, 24750",
    })
    void digitsOneToEightProduceABill(String subscriber, int digit, MockRule rule, long amountDue) {
        InquiryDecision decision = engine.evaluateInquiry(cairoElectricity, subscriber, NOW);

        assertThat(decision.rule()).isEqualTo(rule);
        assertThat(decision.fees().amountDue()).isEqualTo(amountDue);
    }

    @Test
    void digitZeroIsSubscriberNotFound() {
        assertThatThrownBy(() -> engine.evaluateInquiry(cairoElectricity, "1024750890", NOW))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SUBSCRIBER_NOT_FOUND);
    }

    @Test
    void digitNineIsBillAlreadyPaid() {
        assertThatThrownBy(() -> engine.evaluateInquiry(cairoElectricity, "1024750899", NOW))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BILL_ALREADY_PAID);
    }

    @Test
    void largeBillIsAboveTheServiceMaximum() {
        BillerService water = service(500, 300_000);

        InquiryDecision decision = engine.evaluateInquiry(water, "101426006", NOW);

        assertThat(decision.fees().amountDue()).isEqualTo(310_000).isGreaterThan(300_000);
    }

    @Test
    void amountBelowTheMinimumIsRaisedToIt() {
        InquiryDecision decision = engine.evaluateInquiry(cairoElectricity, "1000000001", NOW);

        assertThat(decision.fees().amountDue()).isEqualTo(500);
    }

    @Test
    void sameInputAlwaysGivesTheSameDecision() {
        assertThat(engine.evaluateInquiry(cairoElectricity, "1012345673", NOW))
                .isEqualTo(engine.evaluateInquiry(cairoElectricity, "1012345673", NOW));
    }

    @Test
    void billMonthIsThePreviousUtcMonth() {
        assertThat(MockPaymentEngine.billMonth(Instant.parse("2026-01-01T00:30:00Z")))
                .isEqualTo("2025-12");
        assertThat(MockPaymentEngine.billMonth(Instant.parse("2026-09-30T23:59:59Z")))
                .isEqualTo("2026-08");
    }

    private static BillerService service(long min, long max) {
        BillerService service = mock(BillerService.class);
        when(service.getMinAmount()).thenReturn(min);
        when(service.getMaxAmount()).thenReturn(max);
        return service;
    }
}
