package com.momknpay.ad.web.dto;

import java.util.List;

/** The home-screen ads, in display order. */
public record AdListResponse(List<AdItem> items) {}
