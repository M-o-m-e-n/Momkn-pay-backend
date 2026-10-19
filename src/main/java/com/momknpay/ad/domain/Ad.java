package com.momknpay.ad.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A home-screen banner: an image that opens {@code linkUrl} in the browser (FR-AD). */
@Entity
@Table(name = "ads")
public class Ad {

    @Id private String id;

    @Column(name = "title_en", nullable = false, length = 120)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 120)
    private String titleAr;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "link_url", nullable = false, length = 500)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Ad() {}

    public String getId() {
        return id;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getTitleAr() {
        return titleAr;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
