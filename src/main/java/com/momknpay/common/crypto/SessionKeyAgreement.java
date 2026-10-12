package com.momknpay.common.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.HKDFParameterSpec;
import org.springframework.stereotype.Component;

/**
 * Establishes a session's AES-256 key without ever sending it (ADR-013, LLD §8.3). The only class
 * allowed to touch {@link KeyAgreement} and {@link KDF}.
 *
 * <p>Two ECDH results on P-256 feed HKDF-SHA256:
 *
 * <ul>
 *   <li>client ephemeral × server <b>static</b> key: only the real server can compute it, so an
 *       intermediary that swaps keys cannot learn the session key
 *   <li>client ephemeral × server <b>ephemeral</b> key: forward secrecy, a later leak of the static
 *       key does not expose recorded sessions
 * </ul>
 *
 * Public keys travel as base64 of their X.509 SubjectPublicKeyInfo encoding.
 */
@Component
public class SessionKeyAgreement {

    private static final String CURVE = "secp256r1";
    private static final String INFO_PREFIX = "momknpay/v4/session";
    private static final byte[] PROOF_MESSAGE = "momknpay".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom random;
    private final ECParameterSpec p256;

    public SessionKeyAgreement(SecureRandom random) {
        this.random = random;
        this.p256 = p256Parameters();
    }

    /**
     * The server's half of a handshake.
     *
     * @param serverPublicKey the server's ephemeral public key, to return to the client
     * @param sessionKey the derived 32-byte key; the caller zeroes it after use
     */
    public record Handshake(String serverPublicKey, byte[] sessionKey) {
        @Override
        public String toString() {
            return "Handshake[serverPublicKey=%s, sessionKey=****]".formatted(serverPublicKey);
        }
    }

    /**
     * @throws CryptoException if the key is not valid base64, not an EC key, not on P-256, or not a
     *     point on the curve
     */
    public ECPublicKey parsePublicKey(String spkiBase64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(spkiBase64);
            PublicKey key =
                    KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
            if (!(key instanceof ECPublicKey ecKey) || !isValidP256Point(ecKey)) {
                throw new CryptoException("not a P-256 public key");
            }
            return ecKey;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new CryptoException("malformed public key", e);
        }
    }

    /**
     * Derives the session key for one new session and returns it with the fresh server ephemeral
     * public key. The key is bound to this session, user and key exchange through the HKDF info.
     */
    public Handshake respond(
            ECPrivateKey serverStaticKey,
            ECPublicKey clientKey,
            String clientKeyBase64,
            String sessionId,
            String userId) {
        byte[] staticSecret = null;
        byte[] ephemeralSecret = null;
        byte[] keyMaterial = null;
        try {
            KeyPair ephemeral = generateKeyPair();
            String serverPublicKey = encode(ephemeral.getPublic());
            staticSecret = ecdh(serverStaticKey, clientKey);
            ephemeralSecret = ecdh(ephemeral.getPrivate(), clientKey);
            keyMaterial = concat(staticSecret, ephemeralSecret);
            byte[] info = info(sessionId, userId, clientKeyBase64, serverPublicKey);
            KDF hkdf = KDF.getInstance("HKDF-SHA256");
            byte[] sessionKey =
                    hkdf.deriveData(
                            HKDFParameterSpec.ofExtract()
                                    .addIKM(keyMaterial)
                                    .thenExpand(info, AesGcmCipher.KEY_LENGTH));
            return new Handshake(serverPublicKey, sessionKey);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("key agreement failed", e);
        } finally {
            zero(staticSecret);
            zero(ephemeralSecret);
            zero(keyMaterial);
        }
    }

    /**
     * The public key that belongs to a P-256 private key. The JDK has no direct API for this: the x
     * coordinate comes from ECDH with the curve's base point, y from the curve equation, and its
     * sign is settled by checking a signature made with the private key.
     */
    public ECPublicKey derivePublicKey(ECPrivateKey privateKey) {
        byte[] x = null;
        try {
            if (!sameCurve(privateKey.getParams())) {
                throw new CryptoException("not a P-256 private key");
            }
            KeyFactory factory = KeyFactory.getInstance("EC");
            PublicKey basePoint =
                    factory.generatePublic(new ECPublicKeySpec(p256.getGenerator(), p256));
            x = ecdh(privateKey, basePoint);
            BigInteger p = ((ECFieldFp) p256.getCurve().getField()).getP();
            BigInteger px = new BigInteger(1, x);
            BigInteger py = curveRightSide(px, p).modPow(p.add(BigInteger.ONE).shiftRight(2), p);

            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey, random);
            signer.update(PROOF_MESSAGE);
            byte[] proof = signer.sign();
            for (BigInteger y : new BigInteger[] {py, p.subtract(py)}) {
                PublicKey candidate =
                        factory.generatePublic(new ECPublicKeySpec(new ECPoint(px, y), p256));
                Signature verifier = Signature.getInstance("SHA256withECDSA");
                verifier.initVerify(candidate);
                verifier.update(PROOF_MESSAGE);
                if (verifier.verify(proof)) {
                    return (ECPublicKey) candidate;
                }
            }
            throw new CryptoException("public key derivation failed");
        } catch (GeneralSecurityException e) {
            throw new CryptoException("public key derivation failed", e);
        } finally {
            zero(x);
        }
    }

    /** base64 of the X.509 SubjectPublicKeyInfo encoding. */
    public static String encode(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Raw ECDH: the x coordinate of the shared point (32 bytes on P-256). */
    static byte[] ecdh(PrivateKey privateKey, PublicKey publicKey) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    /** {@code momknpay/v4/session|sessionId|userId|clientPublicKey|serverPublicKey}, UTF-8. */
    static byte[] info(
            String sessionId, String userId, String clientPublicKey, String serverPublicKey) {
        return String.join("|", INFO_PREFIX, sessionId, userId, clientPublicKey, serverPublicKey)
                .getBytes(StandardCharsets.UTF_8);
    }

    private KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(CURVE), random);
        return generator.generateKeyPair();
    }

    private boolean isValidP256Point(ECPublicKey key) {
        ECPoint point = key.getW();
        if (!sameCurve(key.getParams()) || point.equals(ECPoint.POINT_INFINITY)) {
            return false;
        }
        BigInteger p = ((ECFieldFp) p256.getCurve().getField()).getP();
        BigInteger x = point.getAffineX();
        BigInteger y = point.getAffineY();
        boolean inField =
                x.signum() >= 0 && x.compareTo(p) < 0 && y.signum() >= 0 && y.compareTo(p) < 0;
        return inField && y.multiply(y).mod(p).equals(curveRightSide(x, p));
    }

    /** x³ + ax + b mod p. */
    private BigInteger curveRightSide(BigInteger x, BigInteger p) {
        EllipticCurve curve = p256.getCurve();
        return x.pow(3).add(curve.getA().multiply(x)).add(curve.getB()).mod(p);
    }

    private boolean sameCurve(ECParameterSpec params) {
        return params != null
                && params.getCurve().equals(p256.getCurve())
                && params.getGenerator().equals(p256.getGenerator())
                && params.getOrder().equals(p256.getOrder());
    }

    private static ECParameterSpec p256Parameters() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("P-256 is not available", e);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static void zero(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
