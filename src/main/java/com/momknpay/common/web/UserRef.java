package com.momknpay.common.web;

/** The user a request acts for. Controllers get this, never the {@code User} entity. */
public record UserRef(String id) {}
