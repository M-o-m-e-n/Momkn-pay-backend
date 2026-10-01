package com.momknpay.payment.service;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.catalog.repository.BillerServiceRepository;
import com.momknpay.common.config.AppProperties;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.IdGenerator;
import com.momknpay.common.util.Masking;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.payload.service.PayloadDecryptor;
import com.momknpay.payment.domain.Inquiry;
import com.momknpay.payment.engine.Fees;
import com.momknpay.payment.engine.InquiryDecision;
import com.momknpay.payment.engine.MockPaymentEngine;
import com.momknpay.payment.repository.InquiryRepository;
import com.momknpay.payment.web.dto.InquiryPayload;
import com.momknpay.payment.web.dto.InquiryRequest;
import com.momknpay.payment.web.dto.InquiryResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fees inquiry (FR-INQ, LLD §9.3). Check order: service exists → service active → decrypt (blob,
 * replay) → subscriber matches the service's inputPattern → mock rule → persist a 5-minute quote.
 *
 * <p>Deliberately not {@code @Transactional}: the single insert commits on its own, so the {@code
 * _slow} delay afterwards holds no connection or lock.
 */
@Service
public class InquiryService {

    private static final Logger log = LoggerFactory.getLogger(InquiryService.class);

    private final BillerServiceRepository services;
    private final InquiryRepository inquiries;
    private final PayloadDecryptor decryptor;
    private final MockPaymentEngine engine;
    private final SlowServiceDelay slowDelay;
    private final IdGenerator ids;
    private final TimeProvider time;
    private final AppProperties properties;
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    public InquiryService(
            BillerServiceRepository services,
            InquiryRepository inquiries,
            PayloadDecryptor decryptor,
            MockPaymentEngine engine,
            SlowServiceDelay slowDelay,
            IdGenerator ids,
            TimeProvider time,
            AppProperties properties) {
        this.services = services;
        this.inquiries = inquiries;
        this.decryptor = decryptor;
        this.engine = engine;
        this.slowDelay = slowDelay;
        this.ids = ids;
        this.time = time;
        this.properties = properties;
    }

    public InquiryResponse inquire(String userId, InquiryRequest request) {
        BillerService service =
                services.findByIdAndDeletedAtIsNull(request.serviceId())
                        .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND));
        try {
            if (!service.isActive()) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            InquiryPayload payload = decryptor.decrypt(request.payload(), InquiryPayload.class);
            String subscriber = payload.subscriberNumber();
            if (subscriber == null || !matches(service.getInputPattern(), subscriber)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "subscriberNumber");
            }

            Instant now = time.now();
            InquiryDecision decision = engine.evaluateInquiry(service, subscriber, now);
            Fees fees = decision.fees();
            Inquiry inquiry =
                    inquiries.save(
                            new Inquiry(
                                    ids.inquiryId(),
                                    userId,
                                    service,
                                    subscriber,
                                    decision.customerName(),
                                    decision.billMonth(),
                                    fees.amountDue(),
                                    fees.serviceFee(),
                                    fees.vat(),
                                    decision.rule(),
                                    now,
                                    now.plus(properties.inquiryTtl())));
            log.info(
                    "inquiry.created inquiryId={} serviceId={} subscriber={} rule={}",
                    inquiry.getId(),
                    service.getId(),
                    Masking.subscriber(subscriber),
                    decision.rule());
            return InquiryResponse.from(inquiry);
        } finally {
            slowDelay.applyIf(service); // every response for a _slow service is late, errors too
        }
    }

    private boolean matches(String regex, String value) {
        return compiledPatterns.computeIfAbsent(regex, Pattern::compile).matcher(value).matches();
    }
}
