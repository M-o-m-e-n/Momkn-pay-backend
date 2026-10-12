package com.momknpay.session.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/sessions}.
 *
 * @param clientPublicKey the client's ephemeral P-256 public key for this session: base64 of its
 *     X.509 SubjectPublicKeyInfo encoding
 */
public record CreateSessionRequest(@NotBlank @Size(max = 256) String clientPublicKey) {}
