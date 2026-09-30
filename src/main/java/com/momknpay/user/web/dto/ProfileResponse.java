package com.momknpay.user.web.dto;

import com.momknpay.user.domain.User;
import java.time.Instant;

public record ProfileResponse(
        String id, String fullName, String mobile, String email, Instant memberSince) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getMobile(),
                user.getEmail(),
                user.getMemberSince());
    }
}
