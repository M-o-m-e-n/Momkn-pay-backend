package com.momknpay.support;

import com.momknpay.common.web.Headers;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Adds the headers every {@code /v1} call must carry, as a real client would. */
public final class ApiRequests {

    private ApiRequests() {}

    public static MockHttpServletRequestBuilder withClientHeaders(
            MockHttpServletRequestBuilder builder) {
        return builder.header(Headers.REQUEST_ID, UUID.randomUUID().toString())
                .header(Headers.CLIENT_PLATFORM, "ios")
                .header(Headers.CLIENT_VERSION, "1.0.0");
    }
}
