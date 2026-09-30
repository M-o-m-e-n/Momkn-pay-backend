package com.momknpay.user.web;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.Headers;
import com.momknpay.common.web.UserRef;
import com.momknpay.user.service.UserLookupService;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@code @CurrentUser UserRef} from the {@code X-User-Id} header.
 *
 * <p>There is no authentication (ADR-001): the header is trusted. This class is the single place
 * where real authentication would plug in — resolve the user from a verified token here and no
 * controller changes (ADR-002).
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final int MAX_USER_ID_LENGTH = 32;

    private final UserLookupService userLookup;

    public CurrentUserArgumentResolver(UserLookupService userLookup) {
        this.userLookup = userLookup;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UserRef.class.equals(parameter.getParameterType());
    }

    @Override
    public UserRef resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(Headers.USER_ID);
        if (userId == null || userId.isBlank() || userId.length() > MAX_USER_ID_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, Headers.USER_ID);
        }
        return userLookup.require(userId);
    }
}
