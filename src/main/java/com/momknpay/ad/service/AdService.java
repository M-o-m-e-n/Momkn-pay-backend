package com.momknpay.ad.service;

import com.momknpay.ad.repository.AdRepository;
import com.momknpay.ad.web.dto.AdItem;
import com.momknpay.ad.web.dto.AdListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The home-screen ads (FR-AD). */
@Service
public class AdService {

    private final AdRepository ads;

    public AdService(AdRepository ads) {
        this.ads = ads;
    }

    /** FR-AD-1: active ads only, in display order. */
    @Transactional(readOnly = true)
    public AdListResponse getActive() {
        return new AdListResponse(
                ads.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(AdItem::from).toList());
    }
}
