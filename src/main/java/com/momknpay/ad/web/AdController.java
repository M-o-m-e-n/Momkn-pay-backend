package com.momknpay.ad.web;

import com.momknpay.ad.service.AdService;
import com.momknpay.ad.web.dto.AdListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, like the catalogue — no {@code X-User-Id} needed (FR-AD-3). */
@RestController
@Tag(name = "Ads", description = "Home-screen banners that open a link in the browser")
@RequestMapping("/ads")
class AdController {

    private final AdService adService;

    AdController(AdService adService) {
        this.adService = adService;
    }

    @Operation(
            summary = "Active home-screen ads, in display order",
            description = "Show imageUrl; on tap, open linkUrl (always https) in the browser.")
    @ApiResponse(responseCode = "200", description = "Ads")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header or input; `field` names it")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @GetMapping
    AdListResponse list() {
        return adService.getActive();
    }
}
