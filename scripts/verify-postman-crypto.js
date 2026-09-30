// Checks postman/momkn-encrypt.js against Node's AES-256-GCM, byte for byte.
// Run: node scripts/verify-postman-crypto.js
const crypto = require('crypto');
const path = require('path');
const { momknEncrypt } = require(path.join(__dirname, '..', 'postman', 'momkn-encrypt.js'));

let failures = 0;
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

    if (ours !== reference) {
        failures++;
        if (failures <= 3) console.error('MISMATCH', { plaintext, ours, reference });
    }
}
console.log(failures === 0 ? 'OK: 200/200 match Node crypto' : `FAILED: ${failures}/200`);
process.exit(failures === 0 ? 0 : 1);
