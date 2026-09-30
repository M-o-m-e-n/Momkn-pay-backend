package com.momknpay.user.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.UserRef;
import com.momknpay.user.service.ProfileService;
import com.momknpay.user.web.dto.ProfileResponse;
import com.momknpay.user.web.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
class ProfileController {

    private final ProfileService profileService;

    ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    ProfileResponse get(@CurrentUser UserRef user) {
        return profileService.get(user.id());
    }

    @PatchMapping
    ProfileResponse update(
            @CurrentUser UserRef user, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(user.id(), request);
    }
}
