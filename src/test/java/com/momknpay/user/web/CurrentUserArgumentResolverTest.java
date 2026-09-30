package com.momknpay.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.web.Headers;
import com.momknpay.common.web.UserRef;
import com.momknpay.user.service.UserLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentUserArgumentResolverTest {

    private final UserLookupService lookup = mock(UserLookupService.class);
    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(lookup);

    @Test
    void existingUserResolvesToUserRef() {
        when(lookup.require("usr_01")).thenReturn(new UserRef("usr_01"));

        UserRef user = resolve("usr_01");

        assertThat(user).isEqualTo(new UserRef("usr_01"));
    }

    @Test
    void missingHeaderIsValidationErrorNamingTheHeader() {
        assertValidationError(null);
        assertValidationError("   ");
        assertValidationError("usr_" + "x".repeat(29)); // 33 characters
        verify(lookup, never()).require(any());
    }

    @Test
    void unknownUserIsUserNotFound() {
        when(lookup.require("usr_404")).thenThrow(new ApiException(ErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> resolve("usr_404"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private void assertValidationError(String header) {
        assertThatThrownBy(() -> resolve(header))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                            assertThat(e.getField()).isEqualTo(Headers.USER_ID);
                        });
    }

    private UserRef resolve(String header) {
        var request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(Headers.USER_ID, header);
        }
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }
}
