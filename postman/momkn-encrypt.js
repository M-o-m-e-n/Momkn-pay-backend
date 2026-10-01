/*
 * AES-256-GCM payload encryption for the Postman collection (the Postman/Newman sandbox has no
 * AES-GCM). Same wire format as the apps: base64( iv[12] ‖ ciphertext ‖ tag[16] ), no AAD.
 *
 * This file is the readable source. The collection embeds the momknEncrypt function verbatim
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
    if (key.length !== 32) throw new Error('the payload key must be base64 of 32 bytes');
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

if (typeof module !== 'undefined') module.exports = { momknEncrypt };
