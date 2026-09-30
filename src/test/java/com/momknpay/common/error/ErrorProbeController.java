package com.momknpay.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that trigger each kind of error the handler must shape. */
@RestController
@Validated
@RequestMapping("/test/errors")
class ErrorProbeController {

    record ProbeRequest(@NotBlank @Size(max = 5) String name) {}

    @GetMapping("/api")
    void api() {
        throw new ApiException(ErrorCode.INQUIRY_EXPIRED);
    }

    @GetMapping("/api-field")
    void apiWithField() {
        throw new ApiException(ErrorCode.EMAIL_ALREADY_USED, "email");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("secret internal detail");
    }

    @PostMapping("/body")
    ProbeRequest body(@Valid @RequestBody ProbeRequest request) {
        return request;
    }

    @GetMapping("/header")
    String header(@RequestHeader("X-Session-Id") String sessionId) {
        return sessionId;
    }

    @GetMapping("/param")
    int param(@RequestParam("size") @Max(50) int size) {
        return size;
    }
}
