package com.momknpay.user.service;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.web.UserRef;
import com.momknpay.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirms that the user named by {@code X-User-Id} exists (FR-COM-4). */
@Service
public class UserLookupService {

    private final UserRepository users;

    public UserLookupService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserRef require(String userId) {
        if (!users.existsById(userId)) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        return new UserRef(userId);
    }
}
