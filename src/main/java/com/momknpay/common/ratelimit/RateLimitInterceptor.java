package com.momknpay.common.ratelimit;

import com.momknpay.common.web.Headers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the per-user limit to {@code POST /v1/payments/confirm} (FR-PAY-11). Without {@code
 * X-User-Id} it does nothing: the request is rejected by the current-user resolver anyway.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String CONFIRM_PATH = "/v1/payments/confirm";

    private static final Map<String, RateLimiter.Policy> POLICIES =
            Map.of(CONFIRM_PATH, RateLimiter.Policy.CONFIRM);

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        RateLimiter.Policy policy = POLICIES.get(request.getRequestURI());
        String userId = request.getHeader(Headers.USER_ID);
        if (policy != null
                && HttpMethod.POST.matches(request.getMethod())
                && userId != null
                && !userId.isBlank()) {
            rateLimiter.consume(policy, userId);
        }
        return true;
    }
}
