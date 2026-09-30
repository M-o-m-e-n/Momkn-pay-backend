package com.momknpay.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.web.UserRef;
import com.momknpay.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

class UserLookupServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserLookupService service = new UserLookupService(users);

    @Test
    void existingUserIsReturnedAsRef() {
        when(users.existsById("usr_02")).thenReturn(true);

        assertThat(service.require("usr_02")).isEqualTo(new UserRef("usr_02"));
    }

    @Test
    void unknownUserIsUserNotFound() {
        when(users.existsById("usr_99")).thenReturn(false);

        assertThatThrownBy(() -> service.require("usr_99"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
