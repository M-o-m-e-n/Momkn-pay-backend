package com.momknpay.catalog.web;

import com.momknpay.catalog.service.CatalogService;
import com.momknpay.catalog.web.dto.CatalogResponse;
import com.momknpay.catalog.web.dto.SyncResponse;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalogue endpoints — no {@code X-User-Id} needed (FR-CAT-7). */
@RestController
@RequestMapping("/services")
class CatalogController {

    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    CatalogResponse list() {
        return catalogService.getAll();
    }

    /** A missing or unparseable {@code since} is VALIDATION_ERROR with field "since". */
    @GetMapping("/sync")
    SyncResponse sync(
            @RequestParam("since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant since) {
        return catalogService.sync(since);
    }
}
