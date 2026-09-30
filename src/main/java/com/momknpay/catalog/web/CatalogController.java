package com.momknpay.catalog.web;

import com.momknpay.catalog.service.CatalogService;
import com.momknpay.catalog.web.dto.CatalogResponse;
import com.momknpay.catalog.web.dto.SyncResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalogue endpoints — no {@code X-User-Id} needed (FR-CAT-7). */
@RestController
@Tag(name = "Catalogue", description = "Payable services and delta sync for offline-first clients")
@RequestMapping("/services")
class CatalogController {

    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Operation(
            summary = "Full catalogue (inactive services included, deleted ones excluded)",
            description = "Store syncedAt and send it as since on the next /services/sync.")
    @ApiResponse(responseCode = "200", description = "Catalogue snapshot")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header or input; `field` names it")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @GetMapping
    CatalogResponse list() {
        return catalogService.getAll();
    }

    /** A missing or unparseable {@code since} is VALIDATION_ERROR with field "since". */
    @Operation(
            summary = "Services changed or deleted at or after since",
            description =
                    "At-least-once: clients upsert items and delete deletedIds; a few rows may"
                            + " arrive twice.")
    @ApiResponse(responseCode = "200", description = "Delta")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header or input; `field` names it")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @GetMapping("/sync")
    SyncResponse sync(
            @RequestParam("since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant since) {
        return catalogService.sync(since);
    }
}
