package com.momknpay.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.catalog.repository.BillerServiceRepository;
import com.momknpay.common.config.AppProperties;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.IdGenerator;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.payment.engine.MockPaymentEngine;
import com.momknpay.payment.repository.InquiryRepository;
import com.momknpay.payment.web.dto.InquiryPayload;
import com.momknpay.payment.web.dto.InquiryRequest;
import com.momknpay.session.service.PayloadDecryptor;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** U10: inactive services are unavailable, and _slow services are delayed on every response. */
class InquiryServiceTest {

    private final BillerServiceRepository services = mock(BillerServiceRepository.class);
    private final PayloadDecryptor decryptor = mock(PayloadDecryptor.class);
    private final SlowServiceDelay slowDelay = mock(SlowServiceDelay.class);
    private final InquiryService inquiryService =
            new InquiryService(
                    services,
                    mock(InquiryRepository.class),
                    decryptor,
                    mock(MockPaymentEngine.class),
                    slowDelay,
                    mock(IdGenerator.class),
                    mock(TimeProvider.class),
                    new AppProperties(
                            "unused",
                            Duration.ofMinutes(30),
                            Duration.ofMinutes(5),
                            Duration.ofSeconds(120),
                            Duration.ofMinutes(5),
                            Duration.ofSeconds(8),
                            Duration.ofSeconds(10),
                            new AppProperties.RateLimit(5)));

    @Test
    void inactiveServiceIsUnavailableWithoutDecrypting() {
        BillerService alex = service("svc_elec_alex", false);

        assertFails(new InquiryRequest("svc_elec_alex", "blob"), ErrorCode.SERVICE_UNAVAILABLE);
        verify(decryptor, never()).decrypt(anyString(), anyString(), anyString(), any());
        verify(slowDelay).applyIf(alex);
    }

    @Test
    void unknownServiceIsNotFound() {
        when(services.findByIdAndDeletedAtIsNull("svc_nope")).thenReturn(Optional.empty());

        assertFails(new InquiryRequest("svc_nope", "blob"), ErrorCode.SERVICE_NOT_FOUND);
    }

    @Test
    void slowServiceIsDelayedEvenWhenTheInquiryFails() {
        BillerService canal = service("svc_elec_canal_slow", true);
        when(canal.getInputPattern()).thenReturn("^[0-9]{10}$");
        when(decryptor.decrypt(eq("ses_1"), eq("usr_01"), eq("blob"), eq(InquiryPayload.class)))
                .thenReturn(new InquiryPayload("123", "0".repeat(32), 1L)); // fails the pattern

        assertFails(new InquiryRequest("svc_elec_canal_slow", "blob"), ErrorCode.VALIDATION_ERROR);
        verify(slowDelay).applyIf(canal);
    }

    private BillerService service(String id, boolean active) {
        BillerService service = mock(BillerService.class);
        when(service.getId()).thenReturn(id);
        when(service.isActive()).thenReturn(active);
        when(services.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(service));
        return service;
    }

    private void assertFails(InquiryRequest request, ErrorCode code) {
        assertThatThrownBy(() -> inquiryService.inquire("usr_01", "ses_1", request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
