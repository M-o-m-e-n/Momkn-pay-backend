package com.momknpay.ad.web.dto;

import com.momknpay.ad.domain.Ad;

/**
 * One banner. The app shows {@code imageUrl} and opens {@code linkUrl} (always https) in the
 * browser; the titles are the accessibility label and the fallback when the image fails.
 */
public record AdItem(String id, String titleEn, String titleAr, String imageUrl, String linkUrl) {

    public static AdItem from(Ad ad) {
        return new AdItem(
                ad.getId(), ad.getTitleEn(), ad.getTitleAr(), ad.getImageUrl(), ad.getLinkUrl());
    }
}
