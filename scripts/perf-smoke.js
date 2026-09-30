// Performance smoke test (M4-S3, NFR-PER-1): p95 < 300 ms per endpoint, < 500 ms for confirm
// (bcrypt cost 12 alone takes ~230 ms — ADR-010); _slow services are excluded.
//
// Run against a running stack (it writes inquiries and payments, so use a throwaway database):
//   docker compose up -d && node scripts/perf-smoke.js && docker compose down -v
// Options: BASE_URL (default https://localhost/v1), REQUESTS (200), CONCURRENCY (10).
//
// Confirm is rate-limited to 5/min per user, so it is sampled 5× each for usr_01 and usr_03
// (usr_02 keeps its empty history). Exits 1 if any p95 exceeds the budget.
const https = require('https');
const crypto = require('crypto');
const path = require('path');
const { momknEncrypt } = require(path.join(__dirname, '..', 'postman', 'momkn-encrypt.js'));

const BASE = new URL(process.env.BASE_URL || 'https://localhost/v1');
const REQUESTS = Number(process.env.REQUESTS || 200);
const CONCURRENCY = Number(process.env.CONCURRENCY || 10);
const BUDGET_MS = 300;
const CONFIRM_BUDGET_MS = 500; // bcrypt(12) PIN check, see docs/DECISIONS.md ADR-010
const agent = new https.Agent({ keepAlive: true, rejectUnauthorized: false, maxSockets: CONCURRENCY });

function call(method, route, { user, session, key, body } = {}) {
    const headers = {
        'X-Request-Id': crypto.randomUUID(),
        'X-Client-Platform': 'ios',
        'X-Client-Version': 'perf-smoke',
    };
    if (user) headers['X-User-Id'] = user;
    if (session) headers['X-Session-Id'] = session;
    if (key) headers['Idempotency-Key'] = key;
    const data = body ? JSON.stringify(body) : null;
    if (data) headers['Content-Type'] = 'application/json';
    return new Promise((resolve, reject) => {
        const started = process.hrtime.bigint();
        const req = https.request(
            { hostname: BASE.hostname, port: BASE.port || 443, path: BASE.pathname + route, method, headers, agent },
            (res) => {
                let text = '';
                res.on('data', (chunk) => (text += chunk));
                res.on('end', () =>
                    resolve({
                        status: res.statusCode,
                        body: text ? JSON.parse(text) : null,
                        ms: Number(process.hrtime.bigint() - started) / 1e6,
                    }));
            });
        req.on('error', reject);
        if (data) req.write(data);
        req.end();
    });
}

function seal(sessionKey, fields) {
    const body = { ...fields, nonce: crypto.randomBytes(16).toString('hex'), ts: Math.floor(Date.now() / 1000) };
    return momknEncrypt(sessionKey, JSON.stringify(body), [...crypto.randomBytes(12)]);
}

async function measure(name, count, concurrency, request, expectStatus) {
    const times = [];
    let next = 0;
    async function worker() {
        while (next < count) {
            next++;
            const res = await request();
            if (res.status !== expectStatus) {
                throw new Error(`${name}: expected ${expectStatus}, got ${res.status} ${JSON.stringify(res.body)}`);
            }
            times.push(res.ms);
        }
    }
    await Promise.all(Array.from({ length: concurrency }, worker));
    times.sort((a, b) => a - b);
    const pct = (p) => times[Math.min(times.length - 1, Math.ceil((p / 100) * times.length) - 1)];
    return { name, n: times.length, p50: pct(50), p95: pct(95), max: times[times.length - 1] };
}

(async () => {
    // warm-up (JIT, connection pool)
    for (let i = 0; i < 30; i++) await call('GET', '/services');

    const sessions = {};
    for (const user of ['usr_01', 'usr_03']) {
        const res = await call('POST', '/sessions', { user });
        if (res.status !== 201) throw new Error(`session for ${user}: ${res.status}`);
        sessions[user] = res.body;
    }
    const mina = sessions.usr_01;

    const results = [];
    results.push(await measure('GET /services', REQUESTS, CONCURRENCY, () => call('GET', '/services'), 200));
    results.push(await measure('GET /services/sync', REQUESTS, CONCURRENCY,
        () => call('GET', '/services/sync?since=2026-09-18T00:00:00Z'), 200));
    results.push(await measure('GET /profile', REQUESTS, CONCURRENCY, () => call('GET', '/profile', { user: 'usr_01' }), 200));
    results.push(await measure('GET /payments/transactions', REQUESTS, CONCURRENCY,
        () => call('GET', '/payments/transactions?size=20', { user: 'usr_01' }), 200));
    results.push(await measure('GET /payments/transactions/{id}', REQUESTS, CONCURRENCY,
        () => call('GET', '/payments/transactions/txn_5500', { user: 'usr_01' }), 200));
    results.push(await measure('POST /payments/inquiry', REQUESTS, CONCURRENCY,
        () => call('POST', '/payments/inquiry', {
            user: 'usr_01', session: mina.sessionId,
            body: { serviceId: 'svc_elec_cairo', payload: seal(mina.sessionKey, { subscriberNumber: '1024750891' }) },
        }), 200));

    // confirm: 5 per user per minute (rate limit), each on its own fresh inquiry
    const confirmTimes = [];
    for (const [user, pin] of [['usr_01', '1234'], ['usr_03', '9999']]) {
        const s = sessions[user];
        for (let i = 0; i < 5; i++) {
            const inquiry = await call('POST', '/payments/inquiry', {
                user, session: s.sessionId,
                body: { serviceId: 'svc_elec_cairo', payload: seal(s.sessionKey, { subscriberNumber: '1024750891' }) },
            });
            const res = await call('POST', '/payments/confirm', {
                user, session: s.sessionId, key: crypto.randomUUID(),
                body: { inquiryId: inquiry.body.inquiryId, payload: seal(s.sessionKey, { pin }) },
            });
            if (res.status !== 200) throw new Error(`confirm: ${res.status} ${JSON.stringify(res.body)}`);
            confirmTimes.push(res.ms);
        }
    }
    confirmTimes.sort((a, b) => a - b);
    results.push({
        name: 'POST /payments/confirm',
        budget: CONFIRM_BUDGET_MS,
        n: confirmTimes.length,
        p50: confirmTimes[Math.ceil(0.5 * confirmTimes.length) - 1],
        p95: confirmTimes[Math.ceil(0.95 * confirmTimes.length) - 1],
        max: confirmTimes[confirmTimes.length - 1],
    });

    const f = (ms) => ms.toFixed(1).padStart(7);
    console.log(`\n${'endpoint'.padEnd(34)} ${'n'.padStart(4)}  p50 ms  p95 ms  max ms  budget`);
    let failed = false;
    for (const r of results) {
        const ok = r.p95 < (r.budget || BUDGET_MS);
        failed ||= !ok;
        const budget = `${r.budget || BUDGET_MS} ms`.padStart(6);
        console.log(`${r.name.padEnd(34)} ${String(r.n).padStart(4)} ${f(r.p50)} ${f(r.p95)} ${f(r.max)}  ${budget} ${ok ? 'OK' : 'OVER'}`);
    }
    console.log(`\nconcurrency ${CONCURRENCY}, p95 budget ${BUDGET_MS} ms (NFR-PER-1)`);
    agent.destroy();
    process.exit(failed ? 1 : 0);
})().catch((e) => {
    console.error(e.message);
    process.exit(2);
});
