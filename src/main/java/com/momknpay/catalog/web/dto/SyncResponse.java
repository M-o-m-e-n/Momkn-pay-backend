package com.momknpay.catalog.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Delta since the client's last {@code syncedAt}: rows to upsert and ids to delete locally.
 * At-least-once — a boundary row may arrive twice, which an upsert tolerates.
 */
public record SyncResponse(Instant syncedAt, List<ServiceItem> items, List<String> deletedIds) {}
