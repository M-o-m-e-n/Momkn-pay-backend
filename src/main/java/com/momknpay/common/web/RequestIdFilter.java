package com.momknpay.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Puts {@code X-Request-Id} in the logging MDC, echoes it back, and writes one access line per
 * request. Never throws: header validation happens in {@link RequiredHeadersInterceptor}, where
 * errors can reach the global handler.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";

    private static final Logger accessLog = LoggerFactory.getLogger("access");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(Headers.REQUEST_ID);
        MDC.put(MDC_KEY, isUuid(requestId) ? requestId : "invalid");
        if (requestId != null) {
            response.setHeader(Headers.REQUEST_ID, requestId);
        }
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            accessLog.info(
                    "method={} path={} status={} durationMs={} platform={} version={} userId={}",
                    request.getMethod(),
                    pathTemplate(request),
                    response.getStatus(),
                    (System.nanoTime() - start) / 1_000_000,
                    request.getHeader(Headers.CLIENT_PLATFORM),
                    request.getHeader(Headers.CLIENT_VERSION),
                    request.getHeader(Headers.USER_ID));
            MDC.remove(MDC_KEY);
        }
    }

    static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Logs {@code /v1/payments/transactions/{id}}, not the concrete id. */
    private static String pathTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }
}
