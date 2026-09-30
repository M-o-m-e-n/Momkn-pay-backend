package com.momknpay.user.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.UserRef;
import com.momknpay.user.service.ProfileService;
import com.momknpay.user.web.dto.ProfileResponse;
import com.momknpay.user.web.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Profile", description = "The current user's profile")
@RequestMapping("/profile")
class ProfileController {

    private final ProfileService profileService;

    ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Current user's profile")
    @ApiResponse(responseCode = "200", description = "Profile")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header or input; `field` names it")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @GetMapping
    ProfileResponse get(@CurrentUser UserRef user) {
        return profileService.get(user.id());
    }

    @Operation(
            summary = "Update full name and/or email (mobile is read-only)",
            description =
                    "At least one field. Any other property, including mobile, is rejected"
                            + " with VALIDATION_ERROR naming it.")
    @ApiResponse(responseCode = "200", description = "Updated profile")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header or input; `field` names it")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_USED")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @PatchMapping
    ProfileResponse update(
            @CurrentUser UserRef user, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(user.id(), request);
    }
}
