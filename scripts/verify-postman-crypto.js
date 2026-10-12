// Checks postman/momkn-encrypt.js against Node's crypto, byte for byte: AES-256-GCM, HKDF-SHA256
// and the P-256 session handshake.
// Run: node scripts/verify-postman-crypto.js
const crypto = require('crypto');
const path = require('path');
const { momknEncrypt, momknHandshake } = require(path.join(__dirname, '..', 'postman', 'momkn-encrypt.js'));

let failures = 0;
function check(name, ours, reference, detail) {
    if (ours === reference) return;
    failures++;
    if (failures <= 3) console.error('MISMATCH', name, Object.assign({ ours, reference }, detail));
}

// ---- AES-256-GCM ------------------------------------------------------------------------------
for (let run = 0; run < 200; run++) {
    const key = crypto.randomBytes(32);
    const iv = crypto.randomBytes(12);
    const length = run % 70; // covers empty, partial and multi-block plaintexts
    const plaintext = JSON.stringify({ pin: '1234', nonce: crypto.randomBytes(16).toString('hex') })
        .padEnd(length, 'x')
        .slice(0, Math.max(length, 1));

    const ours = momknEncrypt(key.toString('base64'), plaintext, [...iv]);

    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    const reference = Buffer.concat([iv, cipher.update(plaintext, 'utf8'), cipher.final(), cipher.getAuthTag()])
        .toString('base64');
    check('aes-gcm', ours, reference, { plaintext });
}

// ---- HKDF-SHA256 (RFC 5869 test cases 1 and 3, then random inputs) -------------------------------
const handshake = momknHandshake();
const hex = (bytes) => Buffer.from(bytes).toString('hex');
check('hkdf rfc5869 tc1',
    hex(handshake.hkdf([...Buffer.alloc(22, 0x0b)], [...Buffer.from('000102030405060708090a0b0c', 'hex')],
        [...Buffer.from('f0f1f2f3f4f5f6f7f8f9', 'hex')], 42)),
    '3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865');
check('hkdf rfc5869 tc3',
    hex(handshake.hkdf([...Buffer.alloc(22, 0x0b)], [], [], 42)),
    '8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8');
for (let run = 0; run < 50; run++) {
    const ikm = crypto.randomBytes(1 + run * 3);
    const salt = run % 2 ? crypto.randomBytes(run) : Buffer.alloc(0);
    const info = crypto.randomBytes(run * 5);
    const length = 1 + (run * 7) % 90;
    check('hkdf', hex(handshake.hkdf([...ikm], [...salt], [...info], length)),
        hex(Buffer.from(crypto.hkdfSync('sha256', ikm, salt, info, length))), { run });
}

// ---- P-256 key pairs and the session key ---------------------------------------------------------
const spki = (keyObject) => keyObject.export({ type: 'spki', format: 'der' }).toString('base64');
const publicKey = (base64) => crypto.createPublicKey({ key: Buffer.from(base64, 'base64'), format: 'der', type: 'spki' });
for (let run = 0; run < 25; run++) {
    // our public key for a private key = Node's
    const seed = crypto.randomBytes(32);
    const client = handshake.generateKeyPair([...seed]);
    const ecdh = crypto.createECDH('prime256v1');
    ecdh.setPrivateKey(Buffer.from(client.privateKey, 'hex'));
    const nodePoint = ecdh.getPublicKey();
    const nodeSpki = Buffer.concat([Buffer.from('3059301306072a8648ce3d020106082a8648ce3d030107034200', 'hex'), nodePoint])
        .toString('base64');
    check('public key', client.publicKey, nodeSpki, { run });

    // our derived session key = Node's, playing the server with Node key objects
    const serverStatic = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
    const serverEphemeral = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
    const sessionId = 'ses_' + crypto.randomBytes(16).toString('hex');
    const userId = 'usr_0' + (1 + (run % 3));
    const ours = handshake.deriveSessionKey(
        client.privateKey, client.publicKey, spki(serverStatic.publicKey), spki(serverEphemeral.publicKey), sessionId, userId);

    const clientKey = publicKey(client.publicKey);
    const keyMaterial = Buffer.concat([
        crypto.diffieHellman({ privateKey: serverStatic.privateKey, publicKey: clientKey }),
        crypto.diffieHellman({ privateKey: serverEphemeral.privateKey, publicKey: clientKey }),
    ]);
    const info = ['momknpay/v4/session', sessionId, userId, client.publicKey, spki(serverEphemeral.publicKey)].join('|');
    const reference = Buffer.from(crypto.hkdfSync('sha256', keyMaterial, Buffer.alloc(0), info, 32)).toString('base64');
    check('session key', ours, reference, { run });
}

console.log(failures === 0
    ? 'OK: AES-GCM 200/200, HKDF 52/52, handshake 25/25 match Node crypto'
    : `FAILED: ${failures} mismatches`);
process.exit(failures === 0 ? 0 : 1);
