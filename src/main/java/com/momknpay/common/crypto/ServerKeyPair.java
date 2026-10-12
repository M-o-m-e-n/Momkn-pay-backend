package com.momknpay.common.crypto;

import com.momknpay.common.config.AppProperties;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * The server's long-term P-256 key pair for the session handshake (ADR-013). The private key comes
 * only from {@code APP_SESSION_PRIVATE_KEY} and never leaves this class's callers; the public key
 * is not a secret: it is built into the apps and served by {@code GET /v1/sessions/public-key}.
 */
@Component
public class ServerKeyPair {

    private static final int KEY_ID_BYTES = 8;

    private final ECPrivateKey privateKey;
    private final String publicKeyBase64;
    private final String keyId;

    public ServerKeyPair(AppProperties properties, SessionKeyAgreement keyAgreement) {
        this.privateKey = decodePrivateKey(properties.sessionPrivateKey());
        ECPublicKey publicKey;
        try {
            publicKey = keyAgreement.derivePublicKey(privateKey);
        } catch (CryptoException e) {
            throw new IllegalStateException("APP_SESSION_PRIVATE_KEY must be a P-256 key");
        }
        this.publicKeyBase64 = SessionKeyAgreement.encode(publicKey);
        this.keyId = keyId(publicKey.getEncoded());
    }

    public ECPrivateKey privateKey() {
        return privateKey;
    }

    /** base64 of the X.509 SubjectPublicKeyInfo encoding. */
    public String publicKeyBase64() {
        return publicKeyBase64;
    }

    /** Short fingerprint of the public key, so clients can tell which key a server uses. */
    public String keyId() {
        return keyId;
    }

    @Override
    public String toString() {
        return "ServerKeyPair[keyId=%s, privateKey=****]".formatted(keyId);
    }

    /** Fails startup unless the value is base64 of a PKCS#8 EC private key. */
    static ECPrivateKey decodePrivateKey(String base64) {
        byte[] encoded;
        try {
            encoded = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_SESSION_PRIVATE_KEY is not valid base64");
        }
        try {
            PrivateKey key =
                    KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded));
            if (key instanceof ECPrivateKey ecKey) {
                return ecKey;
            }
            throw new IllegalStateException("APP_SESSION_PRIVATE_KEY must be an EC private key");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "APP_SESSION_PRIVATE_KEY must be a PKCS#8 EC private key"
                            + " (scripts/generate-session-keypair.sh)");
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static String keyId(byte[] publicKeySpki) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKeySpki);
            return HexFormat.of().formatHex(digest, 0, KEY_ID_BYTES);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("SHA-256 is not available", e);
        }
    }
}
