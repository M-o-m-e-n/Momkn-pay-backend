package com.momknpay.session.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.UserRef;
import com.momknpay.session.service.SessionService;
import com.momknpay.session.web.dto.CreateSessionRequest;
import com.momknpay.session.web.dto.CreateSessionResponse;
import com.momknpay.session.web.dto.ServerPublicKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Sessions", description = "Crypto sessions: key agreement for the AES-256 payload key")
@RequestMapping("/sessions")
class SessionController {

    private final SessionService sessionService;

    SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(
            summary = "The server's long-term public key of the session handshake",
            description =
                    "Not a secret. Apps should use the copy they are built with, so that an"
                        + " intermediary cannot replace it; this endpoint is for tools and checks.")
    @ApiResponse(responseCode = "200", description = "The public key")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header; `field` names it")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @GetMapping("/public-key")
    ServerPublicKeyResponse publicKey() {
        return sessionService.publicKey();
    }

    @Operation(
            summary = "Open a crypto session by key agreement (the key is never sent)",
            description =
                    "Send a fresh P-256 public key. The response holds the server's ephemeral"
                        + " public key; both sides derive the same AES-256 key (ECDH +"
                        + " HKDF-SHA256), valid for 30 minutes. Rate-limited to 5 per minute per"
                        + " user.")
    @ApiResponse(responseCode = "201", description = "Session created")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header or input; `field` names it")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED — see Retry-After")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateSessionResponse create(
            @CurrentUser UserRef user, @Valid @RequestBody CreateSessionRequest request) {
        return sessionService.create(user.id(), request.clientPublicKey());
    }

    @Operation(
            summary = "Revoke a session (the key stops working immediately)",
            description = "Idempotent: revoking an already revoked session also returns 204.")
    @ApiResponse(responseCode = "204", description = "Revoked")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR — missing or invalid header or input; `field` names it")
    @ApiResponse(responseCode = "404", description = "SESSION_NOT_FOUND or USER_NOT_FOUND")
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR")
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
            @CurrentUser UserRef user,
            @PathVariable("sessionId") @Size(max = 40) String sessionId) {
        sessionService.revoke(user.id(), sessionId);
    }
}
