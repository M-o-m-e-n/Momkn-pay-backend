/*
 * AES-256-GCM payload encryption for the Postman collection (the Postman/Newman sandbox has no
 * AES-GCM). Same wire format as the apps: base64( iv[12] ‖ ciphertext ‖ tag[16] ), no AAD.
 *
 * This file is the readable source. The collection embeds momknEncrypt and momknHandshake verbatim
 * (collection-level pre-request script); scripts/verify-postman-crypto.js checks it against Node's
 * crypto. Test tooling only: the IV comes from crypto.getRandomValues when the sandbox has it and
 * falls back to Math.random otherwise — never copy this into an app.
 */
function momknEncrypt(keyBase64, plaintext, ivOverride) {
    // ---- bytes ------------------------------------------------------------------------------
    const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    function fromBase64(text) {
        const clean = text.replace(/[^A-Za-z0-9+/]/g, '');
        const out = [];
        let buffer = 0, bits = 0;
        for (const ch of clean) {
            buffer = (buffer << 6) | B64.indexOf(ch);
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.push((buffer >> bits) & 0xff);
            }
        }
        return out;
    }
    function toBase64(bytes) {
        let out = '';
        for (let i = 0; i < bytes.length; i += 3) {
            const n = (bytes[i] << 16) | ((bytes[i + 1] || 0) << 8) | (bytes[i + 2] || 0);
            out += B64[(n >> 18) & 63] + B64[(n >> 12) & 63];
            out += i + 1 < bytes.length ? B64[(n >> 6) & 63] : '=';
            out += i + 2 < bytes.length ? B64[n & 63] : '=';
        }
        return out;
    }
    function utf8(text) {
        const out = [];
        for (const ch of unescape(encodeURIComponent(text))) out.push(ch.charCodeAt(0));
        return out;
    }
    function randomBytes(n) {
        const out = new Array(n);
        if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
            const buf = new Uint8Array(n);
            crypto.getRandomValues(buf);
            for (let i = 0; i < n; i++) out[i] = buf[i];
        } else {
            for (let i = 0; i < n; i++) out[i] = Math.floor(Math.random() * 256);
        }
        return out;
    }

    // ---- AES-256 block cipher (FIPS-197) -----------------------------------------------------
    const SBOX = new Array(256);
    (function buildSbox() {
        const rotl = (x, s) => ((x << s) | (x >> (8 - s))) & 0xff;
        let p = 1, q = 1;
        do {
            p = (p ^ (p << 1) ^ (p & 0x80 ? 0x1b : 0)) & 0xff; // p × 3
            q ^= q << 1; q ^= q << 2; q ^= q << 4; q &= 0xff;   // q ÷ 3
            if (q & 0x80) q ^= 0x09;
            SBOX[p] = (q ^ rotl(q, 1) ^ rotl(q, 2) ^ rotl(q, 3) ^ rotl(q, 4) ^ 0x63) & 0xff;
        } while (p !== 1);
        SBOX[0] = 0x63;
    })();
    const xtime = (b) => ((b << 1) ^ (b & 0x80 ? 0x1b : 0)) & 0xff;

    function expandKey(key) { // 32-byte key → 60 words of 4 bytes
        const words = [];
        for (let i = 0; i < 8; i++) words.push(key.slice(4 * i, 4 * i + 4));
        let rcon = 1;
        for (let i = 8; i < 60; i++) {
            let t = words[i - 1].slice();
            if (i % 8 === 0) {
                t = [SBOX[t[1]] ^ rcon, SBOX[t[2]], SBOX[t[3]], SBOX[t[0]]];
                rcon = xtime(rcon);
            } else if (i % 8 === 4) {
                t = t.map((b) => SBOX[b]);
            }
            words.push(words[i - 8].map((b, j) => b ^ t[j]));
        }
        return words;
    }

    function encryptBlock(words, input) {
        let s = input.slice();
        const addRoundKey = (round) => {
            for (let i = 0; i < 16; i++) s[i] ^= words[round * 4 + (i >> 2)][i & 3];
        };
        const subShift = () => {
            const t = new Array(16);
            for (let c = 0; c < 4; c++) {
                for (let r = 0; r < 4; r++) t[r + 4 * c] = SBOX[s[r + 4 * ((c + r) % 4)]];
            }
            s = t;
        };
        const mixColumns = () => {
            for (let c = 0; c < 4; c++) {
                const a = s.slice(4 * c, 4 * c + 4);
                const x = a.map(xtime);
                s[4 * c] = x[0] ^ x[1] ^ a[1] ^ a[2] ^ a[3];
                s[4 * c + 1] = a[0] ^ x[1] ^ x[2] ^ a[2] ^ a[3];
                s[4 * c + 2] = a[0] ^ a[1] ^ x[2] ^ x[3] ^ a[3];
                s[4 * c + 3] = x[0] ^ a[0] ^ a[1] ^ a[2] ^ x[3];
            }
        };
        addRoundKey(0);
        for (let round = 1; round < 14; round++) {
            subShift();
            mixColumns();
            addRoundKey(round);
        }
        subShift();
        addRoundKey(14);
        return s;
    }

    // ---- GCM (NIST SP 800-38D) ---------------------------------------------------------------
    function gfMultiply(x, y) {
        const z = new Array(16).fill(0);
        const v = y.slice();
        for (let i = 0; i < 128; i++) {
            if ((x[i >> 3] >> (7 - (i & 7))) & 1) for (let j = 0; j < 16; j++) z[j] ^= v[j];
            const lsb = v[15] & 1;
            for (let j = 15; j > 0; j--) v[j] = ((v[j] >> 1) | ((v[j - 1] & 1) << 7)) & 0xff;
            v[0] >>= 1;
            if (lsb) v[0] ^= 0xe1;
        }
        return z;
    }
    function increment32(counter) {
        const out = counter.slice();
        for (let i = 15; i >= 12; i--) {
            out[i] = (out[i] + 1) & 0xff;
            if (out[i] !== 0) break;
        }
        return out;
    }

    const key = fromBase64(keyBase64);
    if (key.length !== 32) throw new Error('sessionKey must be base64 of 32 bytes');
    const words = expandKey(key);
    const iv = ivOverride || randomBytes(12);
    const hashKey = encryptBlock(words, new Array(16).fill(0));
    const j0 = iv.concat([0, 0, 0, 1]);

    const plain = utf8(plaintext);
    const cipher = [];
    let counter = j0;
    for (let offset = 0; offset < plain.length; offset += 16) {
        counter = increment32(counter);
        const stream = encryptBlock(words, counter);
        for (let i = 0; i < 16 && offset + i < plain.length; i++) {
            cipher.push(plain[offset + i] ^ stream[i]);
        }
    }

    let ghash = new Array(16).fill(0);
    for (let offset = 0; offset < cipher.length; offset += 16) {
        const block = new Array(16).fill(0);
        for (let i = 0; i < 16 && offset + i < cipher.length; i++) block[i] = cipher[offset + i];
        ghash = gfMultiply(ghash.map((b, i) => b ^ block[i]), hashKey);
    }
    const bitLength = cipher.length * 8; // AAD length is 0
    const lengths = new Array(16).fill(0);
    for (let i = 0; i < 4; i++) lengths[15 - i] = (bitLength >>> (8 * i)) & 0xff;
    ghash = gfMultiply(ghash.map((b, i) => b ^ lengths[i]), hashKey);
    const tag = encryptBlock(words, j0).map((b, i) => b ^ ghash[i]);

    return toBase64(iv.concat(cipher, tag));
}

/*
 * Session key agreement for the Postman collection (ADR-013): ECDH on P-256 and HKDF-SHA256 in
 * plain JavaScript, because the sandbox has neither. The session key is derived here and on the
 * server; it is never sent.
 *
 *   key = HKDF-SHA256( ECDH(client, server long-term) ‖ ECDH(client, server ephemeral),
 *                      salt = none,
 *                      info = "momknpay/v4/session|sessionId|userId|clientPublicKey|serverPublicKey" )
 *
 * Public keys are base64 of their X.509 SubjectPublicKeyInfo encoding. Test tooling only: the
 * arithmetic is not constant-time and the random source may fall back to Math.random — never copy
 * this into an app (use CryptoKit / javax.crypto there).
 */
function momknHandshake() {
    // ---- P-256 --------------------------------------------------------------------------------
    const P = BigInt('0xffffffff00000001000000000000000000000000ffffffffffffffffffffffff');
    const N = BigInt('0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551');
    const B = BigInt('0x5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b');
    const G = {
        x: BigInt('0x6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296'),
        y: BigInt('0x4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5'),
    };
    const ZERO = BigInt(0);
    const ONE = BigInt(1);
    const TWO = BigInt(2);
    const THREE = BigInt(3);
    // DER header of a P-256 SubjectPublicKeyInfo; the uncompressed point 04 ‖ X ‖ Y follows
    const SPKI_PREFIX = '3059301306072a8648ce3d020106082a8648ce3d030107034200';

    function mod(a) {
        const r = a % P;
        return r >= ZERO ? r : r + P;
    }
    function inverse(a) { // extended Euclid, modulo P
        let oldR = mod(a);
        let r = P;
        let oldS = ONE;
        let s = ZERO;
        while (r !== ZERO) {
            const q = oldR / r;
            const nextR = oldR - q * r;
            oldR = r;
            r = nextR;
            const nextS = oldS - q * s;
            oldS = s;
            s = nextS;
        }
        return mod(oldS);
    }
    function add(p, q) { // affine coordinates; null is the point at infinity
        if (!p) return q;
        if (!q) return p;
        let slope;
        if (p.x === q.x) {
            if (mod(p.y + q.y) === ZERO) return null;
            slope = mod((THREE * p.x * p.x - THREE) * inverse(TWO * p.y)); // curve a = -3
        } else {
            slope = mod((q.y - p.y) * inverse(q.x - p.x));
        }
        const x = mod(slope * slope - p.x - q.x);
        return { x: x, y: mod(slope * (p.x - x) - p.y) };
    }
    function multiply(scalar, point) { // double-and-add
        let result = null;
        let addend = point;
        let k = scalar;
        while (k > ZERO) {
            if ((k & ONE) === ONE) result = add(result, addend);
            addend = add(addend, addend);
            k >>= ONE;
        }
        return result;
    }
    function isOnCurve(point) {
        return mod(point.y * point.y) === mod(point.x * point.x * point.x - THREE * point.x + B);
    }

    // ---- bytes --------------------------------------------------------------------------------
    const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    function fromBase64(text) {
        const clean = text.replace(/=+$/, '');
        const out = [];
        let bits = 0;
        let value = 0;
        for (let i = 0; i < clean.length; i++) {
            const index = B64.indexOf(clean[i]);
            if (index < 0) throw new Error('invalid base64');
            value = (value << 6) | index;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.push((value >> bits) & 0xff);
            }
        }
        return out;
    }
    function toBase64(bytes) {
        let out = '';
        for (let i = 0; i < bytes.length; i += 3) {
            const a = bytes[i];
            const b = i + 1 < bytes.length ? bytes[i + 1] : 0;
            const c = i + 2 < bytes.length ? bytes[i + 2] : 0;
            out += B64[a >> 2] + B64[((a & 3) << 4) | (b >> 4)];
            out += i + 1 < bytes.length ? B64[((b & 15) << 2) | (c >> 6)] : '=';
            out += i + 2 < bytes.length ? B64[c & 63] : '=';
        }
        return out;
    }
    function toHex(bytes) {
        return bytes.map((b) => ('0' + b.toString(16)).slice(-2)).join('');
    }
    function fromHex(hex) {
        const out = [];
        for (let i = 0; i < hex.length; i += 2) out.push(parseInt(hex.substr(i, 2), 16));
        return out;
    }
    function to32Bytes(value) {
        return fromHex(('0'.repeat(64) + value.toString(16)).slice(-64));
    }
    function ascii(text) {
        const out = [];
        for (let i = 0; i < text.length; i++) out.push(text.charCodeAt(i) & 0xff);
        return out;
    }
    function randomBytes(n) {
        const out = new Array(n);
        if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
            const buf = new Uint8Array(n);
            crypto.getRandomValues(buf);
            for (let i = 0; i < n; i++) out[i] = buf[i];
        } else {
            for (let i = 0; i < n; i++) out[i] = Math.floor(Math.random() * 256);
        }
        return out;
    }

    // ---- public keys --------------------------------------------------------------------------
    function encodePublicKey(point) {
        return toBase64(fromHex(SPKI_PREFIX + '04').concat(to32Bytes(point.x), to32Bytes(point.y)));
    }
    function decodePublicKey(spkiBase64) {
        const hex = toHex(fromBase64(spkiBase64));
        if (hex.length !== 182 || hex.indexOf(SPKI_PREFIX + '04') !== 0) {
            throw new Error('not a P-256 public key (SubjectPublicKeyInfo, uncompressed point)');
        }
        const point = {
            x: BigInt('0x' + hex.substr(54, 64)),
            y: BigInt('0x' + hex.substr(118, 64)),
        };
        if (point.x >= P || point.y >= P || !isOnCurve(point)) throw new Error('point is not on P-256');
        return point;
    }

    // ---- SHA-256, HMAC, HKDF (FIPS 180-4, RFC 2104, RFC 5869) -----------------------------------
    const K = [];
    const H0 = [];
    (function buildConstants() { // fractional parts of the cube / square roots of the first primes
        const isPrime = (n) => {
            for (let d = 2; d * d <= n; d++) if (n % d === 0) return false;
            return true;
        };
        const fraction = (x) => Math.floor((x - Math.floor(x)) * 4294967296);
        for (let candidate = 2; K.length < 64; candidate++) {
            if (!isPrime(candidate)) continue;
            if (H0.length < 8) H0.push(fraction(Math.sqrt(candidate)));
            K.push(fraction(Math.cbrt(candidate)));
        }
    })();
    function sha256(bytes) {
        const message = bytes.slice();
        const bitLength = bytes.length * 8;
        message.push(0x80);
        while (message.length % 64 !== 56) message.push(0);
        const high = Math.floor(bitLength / 4294967296);
        const low = bitLength >>> 0;
        message.push(high >>> 24, (high >>> 16) & 255, (high >>> 8) & 255, high & 255);
        message.push(low >>> 24, (low >>> 16) & 255, (low >>> 8) & 255, low & 255);

        const rotate = (x, n) => (x >>> n) | (x << (32 - n));
        const h = H0.slice();
        const w = new Array(64);
        for (let offset = 0; offset < message.length; offset += 64) {
            for (let i = 0; i < 16; i++) {
                const j = offset + i * 4;
                w[i] = ((message[j] << 24) | (message[j + 1] << 16) | (message[j + 2] << 8) | message[j + 3]) >>> 0;
            }
            for (let i = 16; i < 64; i++) {
                const s0 = rotate(w[i - 15], 7) ^ rotate(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                const s1 = rotate(w[i - 2], 17) ^ rotate(w[i - 2], 19) ^ (w[i - 2] >>> 10);
                w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
            }
            let a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
            for (let i = 0; i < 64; i++) {
                const s1 = rotate(e, 6) ^ rotate(e, 11) ^ rotate(e, 25);
                const t1 = (hh + s1 + ((e & f) ^ (~e & g)) + K[i] + w[i]) >>> 0;
                const s0 = rotate(a, 2) ^ rotate(a, 13) ^ rotate(a, 22);
                const t2 = (s0 + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
                hh = g; g = f; f = e; e = (d + t1) >>> 0;
                d = c; c = b; b = a; a = (t1 + t2) >>> 0;
            }
            h[0] = (h[0] + a) >>> 0; h[1] = (h[1] + b) >>> 0; h[2] = (h[2] + c) >>> 0; h[3] = (h[3] + d) >>> 0;
            h[4] = (h[4] + e) >>> 0; h[5] = (h[5] + f) >>> 0; h[6] = (h[6] + g) >>> 0; h[7] = (h[7] + hh) >>> 0;
        }
        const out = [];
        for (let i = 0; i < 8; i++) out.push(h[i] >>> 24, (h[i] >>> 16) & 255, (h[i] >>> 8) & 255, h[i] & 255);
        return out;
    }
    function hmac(key, data) {
        let block = key.length > 64 ? sha256(key) : key.slice();
        while (block.length < 64) block.push(0);
        const inner = block.map((b) => b ^ 0x36);
        const outer = block.map((b) => b ^ 0x5c);
        return sha256(outer.concat(sha256(inner.concat(data))));
    }
    function hkdf(keyMaterial, salt, info, length) {
        const pseudoRandomKey = hmac(salt.length ? salt : new Array(32).fill(0), keyMaterial);
        let block = [];
        let out = [];
        for (let counter = 1; out.length < length; counter++) {
            block = hmac(pseudoRandomKey, block.concat(info, [counter]));
            out = out.concat(block);
        }
        return out.slice(0, length);
    }

    // ---- the handshake --------------------------------------------------------------------------
    function sharedSecret(privateKeyHex, publicKeyBase64) {
        return to32Bytes(multiply(BigInt('0x' + privateKeyHex), decodePublicKey(publicKeyBase64)).x);
    }
    return {
        /** A fresh key pair for one session: { privateKey (hex), publicKey (base64 SPKI) }. */
        generateKeyPair: function (privateKeyOverride) {
            let scalar;
            do {
                scalar = BigInt('0x' + toHex(privateKeyOverride || randomBytes(32))) % N;
            } while (scalar === ZERO);
            return { privateKey: toHex(to32Bytes(scalar)), publicKey: encodePublicKey(multiply(scalar, G)) };
        },
        /** base64 of the 32-byte session key; the same value the server derives. */
        deriveSessionKey: function (privateKeyHex, clientPublicKey, serverStaticKey, serverEphemeralKey, sessionId, userId) {
            const keyMaterial = sharedSecret(privateKeyHex, serverStaticKey)
                .concat(sharedSecret(privateKeyHex, serverEphemeralKey));
            const info = ['momknpay/v4/session', sessionId, userId, clientPublicKey, serverEphemeralKey].join('|');
            return toBase64(hkdf(keyMaterial, [], ascii(info), 32));
        },
        hkdf: hkdf, // exposed for scripts/verify-postman-crypto.js
    };
}

if (typeof module !== 'undefined') module.exports = { momknEncrypt, momknHandshake };
