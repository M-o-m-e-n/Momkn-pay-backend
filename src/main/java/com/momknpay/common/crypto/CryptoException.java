package com.momknpay.common.crypto;

/**
 * A payload or key could not be decrypted: wrong key, tampered bytes, or malformed input. Callers
 * translate it into {@code DECRYPTION_FAILED}; the message never contains key or payload material.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
