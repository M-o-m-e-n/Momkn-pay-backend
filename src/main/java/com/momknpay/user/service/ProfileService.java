package com.momknpay.user.service;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.user.domain.User;
import com.momknpay.user.repository.UserRepository;
import com.momknpay.user.web.dto.ProfileResponse;
import com.momknpay.user.web.dto.UpdateProfileRequest;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** View and edit the current user's profile (FR-PRO). The mobile number never changes. */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private static final int MIN_NAME_LENGTH = 2;

    private final UserRepository users;
    private final TimeProvider time;

    public ProfileService(UserRepository users, TimeProvider time) {
        this.users = users;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(String userId) {
        return ProfileResponse.from(load(userId));
    }

    /** FR-PRO-2…5: trims the name, lower-cases the email, keeps emails unique. */
    @Transactional
    public ProfileResponse update(String userId, UpdateProfileRequest request) {
        if (request.fullName() == null && request.email() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR); // nothing to update
        }
        String fullName = request.fullName() == null ? null : request.fullName().strip();
        if (fullName != null && fullName.length() < MIN_NAME_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "fullName");
        }
        String email =
                request.email() == null ? null : request.email().strip().toLowerCase(Locale.ROOT);
        if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_USED, "email");
        }

        User user = load(userId);
        user.updateProfile(fullName, email, time.now());
        try {
            users.saveAndFlush(user); // flush now so a racing duplicate hits ux_users_email here
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_USED, "email");
        }
        log.info("profile.updated userId={}", userId);
        return ProfileResponse.from(user);
    }

    private User load(String userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
