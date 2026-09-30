package com.momknpay.catalog.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Full catalogue snapshot.
 *
 * @param syncedAt server time of the snapshot; clients send it back as {@code since}
 */
public record CatalogResponse(Instant syncedAt, List<ServiceItem> items) {}
