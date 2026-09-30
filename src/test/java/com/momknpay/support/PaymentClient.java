package com.momknpay.support;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.common.web.Headers;
import com.momknpay.session.web.dto.CreateSessionResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Drives inquiry and confirm over HTTP for one user and one crypto session, like a client. */
public final class PaymentClient {

    private final MockMvc mvc;
    private final String userId;
    private final CreateSessionResponse session;

    public PaymentClient(MockMvc mvc, String userId, CreateSessionResponse session) {
        this.mvc = mvc;
        this.userId = userId;
        this.session = session;
    }

    public String sessionKey() {
        return session.sessionKey();
    }

    /** Runs an inquiry that must succeed and returns its id. */
    public String openInquiry(String serviceId, String subscriberNumber) throws Exception {
        String body =
                inquire(serviceId, Payloads.inquiry(session.sessionKey(), subscriberNumber))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(body, "$.inquiryId");
    }

    public ResultActions inquire(String serviceId, String payload) throws Exception {
        return mvc.perform(
                withClientHeaders(post("/v1/payments/inquiry"))
                        .header(Headers.USER_ID, userId)
                        .header(Headers.SESSION_ID, session.sessionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"serviceId":"%s","payload":"%s"}\
                                """
                                        .formatted(serviceId, payload)));
    }

    public ResultActions confirm(String inquiryId, UUID idempotencyKey, String pin)
            throws Exception {
        return confirmWithPayload(
                inquiryId, idempotencyKey, Payloads.confirm(session.sessionKey(), pin));
    }

    /** Sends exactly these bytes — used to replay an identical request. */
    public ResultActions confirmWithPayload(String inquiryId, UUID idempotencyKey, String payload)
            throws Exception {
        var request =
                withClientHeaders(post("/v1/payments/confirm"))
                        .header(Headers.USER_ID, userId)
                        .header(Headers.SESSION_ID, session.sessionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"inquiryId":"%s","payload":"%s"}\
                                """
                                        .formatted(inquiryId, payload));
        if (idempotencyKey != null) {
            request.header(Headers.IDEMPOTENCY_KEY, idempotencyKey.toString());
        }
        return mvc.perform(request);
    }
}
