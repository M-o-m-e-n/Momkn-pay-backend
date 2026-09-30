package com.momknpay.common.util;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Unguessable, prefixed ids (LLD §1). Transaction ids come from a database sequence instead. */
@Component
public class IdGenerator {

    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom random;

    public IdGenerator(SecureRandom random) {
        this.random = random;
    }

    /** {@code ses_} + 32 hex characters (128 random bits). */
    public String sessionId() {
        return "ses_" + randomHex(16);
    }

    /** {@code inq_} + 16 hex characters (64 random bits). */
    public String inquiryId() {
        return "inq_" + randomHex(8);
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return HEX.formatHex(buffer);
    }
}
