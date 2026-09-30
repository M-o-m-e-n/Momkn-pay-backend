package com.momknpay.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A payable biller ("service" in the API). Named {@code BillerService} to avoid clashing with
 * Spring's {@code @Service}.
 */
@Entity
@Table(name = "services")
public class BillerService {

    private static final String SLOW_SUFFIX = "_slow";

    @Id private String id;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "name_ar", nullable = false, length = 100)
    private String nameAr;

    @Column(nullable = false, length = 16)
    private ServiceCategory category;

    @Column(name = "icon_url", nullable = false)
    private String iconUrl;

    @Column(name = "input_label", nullable = false, length = 64)
    private String inputLabel;

    @Column(name = "input_pattern", nullable = false, length = 128)
    private String inputPattern;

    @Column(name = "min_amount", nullable = false)
    private long minAmount;

    @Column(name = "max_amount", nullable = false)
    private long maxAmount;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected BillerService() {}

    /** Mock rule: services whose id ends in {@code _slow} respond after a delay. */
    public boolean isSlow() {
        return id.endsWith(SLOW_SUFFIX);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String getId() {
        return id;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameAr() {
        return nameAr;
    }

    public ServiceCategory getCategory() {
        return category;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getInputLabel() {
        return inputLabel;
    }

    public String getInputPattern() {
        return inputPattern;
    }

    public long getMinAmount() {
        return minAmount;
    }

    public long getMaxAmount() {
        return maxAmount;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
