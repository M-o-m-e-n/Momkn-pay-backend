package com.momknpay.support;

/** Encrypted request payloads built the way the mobile clients build them (shared test key). */
public final class Payloads {

    private Payloads() {}

    public static String inquiry(String subscriberNumber) {
        return inquiry(subscriberNumber, TestCrypto.nowTs());
    }

    public static String inquiry(String subscriberNumber, long ts) {
        return TestCrypto.encrypt(
                TestCrypto.TEST_PAYLOAD_KEY,
                """
                {"subscriberNumber":"%s","nonce":"%s","ts":%d}\
                """
                        .formatted(subscriberNumber, TestCrypto.nonce(), ts));
    }

    public static String confirm(String pin) {
        return TestCrypto.encrypt(
                TestCrypto.TEST_PAYLOAD_KEY,
                """
                {"pin":"%s","nonce":"%s","ts":%d}\
                """
                        .formatted(pin, TestCrypto.nonce(), TestCrypto.nowTs()));
    }
}
