package com.momknpay.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Does what the mobile clients do (session handshake and payload encryption), written independently
 * of the server's {@code SessionKeyAgreement} and {@code AesGcmCipher} so tests catch a
 * server/client mismatch.
 */
public final class TestCrypto {

    /** Public half of the test handshake key in src/test/resources/config/application.yml. */
    public static final String SERVER_PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFGYPV1CqxEKYF1QZ1BC2ncpEQGRDDjDxXIhpZabbQHe9JzHv7CPe0yOwH8PpEkhSUu2FwlRdv9rDwrToEvxPig==";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestCrypto() {}

    /** A fresh nonce: 16 random bytes as 32 lowercase hex characters. */
    public static String nonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static long nowTs() {
        return Instant.now().getEpochSecond();
    }

    /** base64( iv[12] ‖ AES-256-GCM(json) ‖ tag[16] ), fresh IV, no AAD. */
    public static String encrypt(String sessionKeyBase64, String json) {
        try {
            byte[] key = Base64.getDecoder().decode(sessionKeyBase64);
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(sealed, 0, blob, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(blob);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A fresh ephemeral P-256 key pair, as a client makes for every session. */
    public static KeyPair clientKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** base64 of the X.509 SubjectPublicKeyInfo encoding, the wire format of public keys. */
    public static String spki(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * The client's derivation of the session key (LLD §8.3): HKDF-SHA256 over ECDH(client, server
     * static) ‖ ECDH(client, server ephemeral), bound to the session by the info string.
     *
     * @return base64 of the 32-byte key
     */
    public static String deriveSessionKey(
            KeyPair client,
            String serverStaticKey,
            String serverEphemeralKey,
            String sessionId,
            String userId) {
        try {
            byte[] staticSecret = ecdh(client.getPrivate(), serverStaticKey);
            byte[] ephemeralSecret = ecdh(client.getPrivate(), serverEphemeralKey);
            byte[] keyMaterial = new byte[staticSecret.length + ephemeralSecret.length];
            System.arraycopy(staticSecret, 0, keyMaterial, 0, staticSecret.length);
            System.arraycopy(
                    ephemeralSecret, 0, keyMaterial, staticSecret.length, ephemeralSecret.length);
            String info =
                    String.join(
                            "|",
                            "momknpay/v4/session",
                            sessionId,
                            userId,
                            spki(client.getPublic()),
                            serverEphemeralKey);
            byte[] key = hkdf(keyMaterial, new byte[0], info.getBytes(StandardCharsets.UTF_8), 32);
            return Base64.getEncoder().encodeToString(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** HKDF-SHA256 (RFC 5869) from plain HMAC, so it does not share code with the server. */
    public static byte[] hkdf(byte[] keyMaterial, byte[] salt, byte[] info, int length)
            throws GeneralSecurityException {
        byte[] pseudoRandomKey = hmac(salt.length == 0 ? new byte[32] : salt, keyMaterial);
        byte[] output = new byte[length];
        byte[] block = new byte[0];
        for (int offset = 0, counter = 1; offset < length; counter++) {
            byte[] input = new byte[block.length + info.length + 1];
            System.arraycopy(block, 0, input, 0, block.length);
            System.arraycopy(info, 0, input, block.length, info.length);
            input[input.length - 1] = (byte) counter;
            block = hmac(pseudoRandomKey, input);
            int take = Math.min(block.length, length - offset);
            System.arraycopy(block, 0, output, offset, take);
            offset += take;
        }
        return output;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] ecdh(PrivateKey privateKey, String publicKeyBase64)
            throws GeneralSecurityException {
        PublicKey publicKey =
                KeyFactory.getInstance("EC")
                        .generatePublic(
                                new X509EncodedKeySpec(
                                        Base64.getDecoder().decode(publicKeyBase64)));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }
}
