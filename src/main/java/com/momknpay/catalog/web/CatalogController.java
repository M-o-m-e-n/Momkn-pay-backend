package com.momknpay.catalog.web;

import com.momknpay.catalog.service.CatalogService;
import com.momknpay.catalog.web.dto.CatalogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
