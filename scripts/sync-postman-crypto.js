// Embeds postman/momkn-encrypt.js into the collection's collection-level pre-request script, so the
// readable source stays the single source of truth. Run after editing momkn-encrypt.js:
//   node scripts/verify-postman-crypto.js && node scripts/sync-postman-crypto.js
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const collectionPath = path.join(root, 'postman', 'momknpay.postman_collection.json');
const { momknEncrypt } = require(path.join(root, 'postman', 'momkn-encrypt.js'));

// Builds a payload: adds a fresh nonce (16 random bytes, hex) and ts, then encrypts.
function momknSeal(keyBase64, fields) {
    const bytes = [];
    for (let i = 0; i < 16; i++) {
        bytes.push(
            typeof crypto !== 'undefined' && crypto.getRandomValues
                ? crypto.getRandomValues(new Uint8Array(1))[0]
                : Math.floor(Math.random() * 256));
    }
    const nonce = bytes.map((b) => ('0' + b.toString(16)).slice(-2)).join('');
    const body = Object.assign({}, fields, { nonce: nonce, ts: Math.floor(Date.now() / 1000) });
    return momknEncrypt(keyBase64, JSON.stringify(body));
}

const librarySource = momknEncrypt.toString() + '\n' + momknSeal.toString();
const exec = [
    '// fresh X-Request-Id for every request',
    "pm.collectionVariables.set('requestId', pm.variables.replaceIn('{{$guid}}'));",
    '',
    '// AES-256-GCM helpers for encrypted payloads (source: postman/momkn-encrypt.js).',
    '// Request scripts load them with: eval(pm.collectionVariables.get(\'momknCrypto\'));',
    'pm.collectionVariables.set(\'momknCrypto\', ' + JSON.stringify(librarySource) + ');',
];

const collection = JSON.parse(fs.readFileSync(collectionPath, 'utf8'));
collection.event = [{ listen: 'prerequest', script: { type: 'text/javascript', exec } }];
if (!collection.variable.some((v) => v.key === 'momknCrypto')) {
    collection.variable.push({ key: 'momknCrypto', value: '' });
}
fs.writeFileSync(collectionPath, JSON.stringify(collection, null, 2) + '\n');
console.log('Embedded momkn-encrypt.js into', path.relative(root, collectionPath));
