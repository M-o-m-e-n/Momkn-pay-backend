package com.momknpay.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH body: either field may be omitted, not both. {@code mobile} is read-only — sending it (or
 * any other property) fails deserialisation and becomes VALIDATION_ERROR naming the property.
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 100) @Pattern(regexp = ".*\\S.*") String fullName,
        @Email @Size(max = 254) String email) {}
