package com.momknpay.payment.engine;

import com.momknpay.payment.domain.MockRule;

/** What the mock engine decided for an inquiry that has a bill. */
public record InquiryDecision(MockRule rule, Fees fees, String customerName, String billMonth) {}
