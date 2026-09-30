package com.momknpay.support;

/** Encrypted request payloads built the way the mobile clients build them. */
public final class Payloads {

    private Payloads() {}

    public static String inquiry(String sessionKey, String subscriberNumber) {
        return inquiry(sessionKey, subscriberNumber, TestCrypto.nowTs());
    }

    public static String inquiry(String sessionKey, String subscriberNumber, long ts) {
        return TestCrypto.encrypt(
                sessionKey,
                """
                {"subscriberNumber":"%s","nonce":"%s","ts":%d}\
                """
                        .formatted(subscriberNumber, TestCrypto.nonce(), ts));
    }

    public static String confirm(String sessionKey, String pin) {
        return TestCrypto.encrypt(
                sessionKey,
                """
                {"pin":"%s","nonce":"%s","ts":%d}\
                """
                        .formatted(pin, TestCrypto.nonce(), TestCrypto.nowTs()));
    }
}
