package com.momknpay.session.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.UserRef;
import com.momknpay.session.service.SessionService;
import com.momknpay.session.web.dto.CreateSessionResponse;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/sessions")
class SessionController {

    private final SessionService sessionService;

    SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateSessionResponse create(@CurrentUser UserRef user) {
        return sessionService.create(user.id());
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
            @CurrentUser UserRef user,
            @PathVariable("sessionId") @Size(max = 40) String sessionId) {
        sessionService.revoke(user.id(), sessionId);
    }
}
