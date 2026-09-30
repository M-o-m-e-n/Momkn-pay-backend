package com.momknpay.common.web;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Every {@code /v1} call must identify itself (SRS FR-COM-1). */
@Component
public class RequiredHeadersInterceptor implements HandlerInterceptor {

    private static final Set<String> PLATFORMS = Set.of("ios", "android");
    private static final int MAX_VERSION_LENGTH = 32;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!RequestIdFilter.isUuid(request.getHeader(Headers.REQUEST_ID))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, Headers.REQUEST_ID);
        }
        String platform = request.getHeader(Headers.CLIENT_PLATFORM);
        if (platform == null || !PLATFORMS.contains(platform)) { // Set.of rejects contains(null)
            throw new ApiException(ErrorCode.VALIDATION_ERROR, Headers.CLIENT_PLATFORM);
        }
        String version = request.getHeader(Headers.CLIENT_VERSION);
        if (version == null || version.isBlank() || version.length() > MAX_VERSION_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, Headers.CLIENT_VERSION);
        }
        return true;
    }
}
