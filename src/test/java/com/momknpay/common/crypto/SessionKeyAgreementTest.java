package com.momknpay.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momknpay.support.TestCrypto;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SessionKeyAgreementTest {

    private static final HexFormat HEX = HexFormat.of();

    private final SessionKeyAgreement agreement = new SessionKeyAgreement(new SecureRandom());

    @Test
    void ecdhMatchesThePublishedTestVector() throws Exception {
        // RFC 5903 §8.1, 256-bit random ECP group
        PrivateKey initiator =
                privateKey("C88F01F510D9AC3F70A292DAA2316DE544E9AAB8AFE84049C62A9C57862D1433");
        PublicKey responder =
                publicKey(
                        "D12DFB5289C8D4F81208B70270398C342296970A0BCCB74C736FC7554494BF63",
                        "56FBF3CA366CC23E8157854C13C58D6AAC23F046ADA30F8353E74F33039872AB");

        byte[] shared = SessionKeyAgreement.ecdh(initiator, responder);

        assertThat(HEX.formatHex(shared))
                .isEqualToIgnoringCase(
                        "D6840F6B42F6EDAFD13116E0E12565202FEF8E9ECE7DCE03812464D04B9442DE");
    }

    @Test
    void theIndependentClientHkdfMatchesThePublishedTestVectors() throws Exception {
        // RFC 5869 test cases 1 and 3: this proves the helper the next tests compare against
        byte[] keyMaterial = HEX.parseHex("0b".repeat(22));

        assertThat(
                        TestCrypto.hkdf(
                                keyMaterial,
                                HEX.parseHex("000102030405060708090a0b0c"),
                                HEX.parseHex("f0f1f2f3f4f5f6f7f8f9"),
                                42))
                .isEqualTo(
                        HEX.parseHex(
                                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                                        + "34007208d5b887185865"));
        assertThat(TestCrypto.hkdf(keyMaterial, new byte[0], new byte[0], 42))
                .isEqualTo(
                        HEX.parseHex(
                                "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d"
                                        + "9d201395faa4b61a96c8"));
    }

    @Test
    void clientAndServerDeriveTheSameKeyWithoutSendingIt() {
        KeyPair serverStatic = TestCrypto.clientKeyPair();
        KeyPair client = TestCrypto.clientKeyPair();
        String clientKey = TestCrypto.spki(client.getPublic());

        SessionKeyAgreement.Handshake handshake =
                agreement.respond(
                        (ECPrivateKey) serverStatic.getPrivate(),
                        agreement.parsePublicKey(clientKey),
                        clientKey,
                        "ses_1",
                        "usr_01");

        String clientSide =
                TestCrypto.deriveSessionKey(
                        client,
                        TestCrypto.spki(serverStatic.getPublic()),
                        handshake.serverPublicKey(),
                        "ses_1",
                        "usr_01");
        assertThat(handshake.sessionKey()).hasSize(32);
        assertThat(Base64.getEncoder().encodeToString(handshake.sessionKey()))
                .isEqualTo(clientSide);
    }

    @Test
    void everyHandshakeGivesADifferentKeyEvenForTheSameClientKey() {
        KeyPair serverStatic = TestCrypto.clientKeyPair();
        String clientKey = TestCrypto.spki(TestCrypto.clientKeyPair().getPublic());
        ECPublicKey parsed = agreement.parsePublicKey(clientKey);
        ECPrivateKey staticKey = (ECPrivateKey) serverStatic.getPrivate();

        SessionKeyAgreement.Handshake first =
                agreement.respond(staticKey, parsed, clientKey, "ses_1", "usr_01");
        SessionKeyAgreement.Handshake second =
                agreement.respond(staticKey, parsed, clientKey, "ses_1", "usr_01");

        assertThat(first.serverPublicKey()).isNotEqualTo(second.serverPublicKey());
        assertThat(first.sessionKey()).isNotEqualTo(second.sessionKey());
    }

    @Test
    void theKeyIsBoundToTheSessionAndTheUser() {
        KeyPair serverStatic = TestCrypto.clientKeyPair();
        KeyPair client = TestCrypto.clientKeyPair();
        String clientKey = TestCrypto.spki(client.getPublic());
        String serverKey = TestCrypto.spki(serverStatic.getPublic());

        SessionKeyAgreement.Handshake handshake =
                agreement.respond(
                        (ECPrivateKey) serverStatic.getPrivate(),
                        agreement.parsePublicKey(clientKey),
                        clientKey,
                        "ses_1",
                        "usr_01");

        String key = Base64.getEncoder().encodeToString(handshake.sessionKey());
        String ephemeral = handshake.serverPublicKey();
        assertThat(TestCrypto.deriveSessionKey(client, serverKey, ephemeral, "ses_2", "usr_01"))
                .isNotEqualTo(key);
        assertThat(TestCrypto.deriveSessionKey(client, serverKey, ephemeral, "ses_1", "usr_02"))
                .isNotEqualTo(key);
    }

    @Test
    void aClientThatDoesNotKnowTheRealServerKeyDerivesAnotherKey() {
        KeyPair serverStatic = TestCrypto.clientKeyPair();
        KeyPair impostor = TestCrypto.clientKeyPair();
        KeyPair client = TestCrypto.clientKeyPair();
        String clientKey = TestCrypto.spki(client.getPublic());

        SessionKeyAgreement.Handshake handshake =
                agreement.respond(
                        (ECPrivateKey) serverStatic.getPrivate(),
                        agreement.parsePublicKey(clientKey),
                        clientKey,
                        "ses_1",
                        "usr_01");

        String withWrongServerKey =
                TestCrypto.deriveSessionKey(
                        client,
                        TestCrypto.spki(impostor.getPublic()),
                        handshake.serverPublicKey(),
                        "ses_1",
                        "usr_01");
        assertThat(withWrongServerKey)
                .isNotEqualTo(Base64.getEncoder().encodeToString(handshake.sessionKey()));
    }

    @Test
    void publicKeyIsDerivedFromThePrivateKey() {
        for (int i = 0; i < 8; i++) { // both signs of y occur
            KeyPair pair = TestCrypto.clientKeyPair();

            ECPublicKey derived = agreement.derivePublicKey((ECPrivateKey) pair.getPrivate());

            assertThat(derived.getEncoded()).isEqualTo(pair.getPublic().getEncoded());
        }
    }

    @Test
    void malformedPublicKeysAreRejected() {
        assertThatThrownBy(() -> agreement.parsePublicKey("not base64 !"))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> agreement.parsePublicKey("")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(
                        () ->
                                agreement.parsePublicKey(
                                        Base64.getEncoder()
                                                .encodeToString(
                                                        "junk".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void aKeyOnAnotherCurveIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        String p384Key = TestCrypto.spki(generator.generateKeyPair().getPublic());

        assertThatThrownBy(() -> agreement.parsePublicKey(p384Key))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void aPointThatIsNotOnTheCurveIsRejected() throws Exception {
        ECPublicKey real = (ECPublicKey) TestCrypto.clientKeyPair().getPublic();
        ECPoint offCurve =
                new ECPoint(real.getW().getAffineX(), real.getW().getAffineY().add(BigInteger.ONE));
        byte[] encoded = real.getEncoded();
        byte[] y = toFixedLength(offCurve.getAffineY());
        System.arraycopy(y, 0, encoded, encoded.length - 32, 32); // SPKI ends with X ‖ Y
        String forged = Base64.getEncoder().encodeToString(encoded);

        assertThatThrownBy(() -> agreement.parsePublicKey(forged))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void aValidKeyIsParsed() {
        KeyPair pair = TestCrypto.clientKeyPair();

        ECPublicKey parsed = agreement.parsePublicKey(TestCrypto.spki(pair.getPublic()));

        assertThat(parsed.getEncoded()).isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void handshakeToStringMasksTheKey() {
        assertThat(new SessionKeyAgreement.Handshake("pub", new byte[32]).toString())
                .contains("pub", "****");
    }

    private static byte[] toFixedLength(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, fixed, 32 - copy, copy);
        return fixed;
    }

    private static PrivateKey privateKey(String hex) throws Exception {
        return KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(hex, 16), p256()));
    }

    private static PublicKey publicKey(String xHex, String yHex) throws Exception {
        ECPoint point = new ECPoint(new BigInteger(xHex, 16), new BigInteger(yHex, 16));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256()));
    }

    private static ECParameterSpec p256() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }
}
