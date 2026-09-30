-- Payment history for usr_01 (docs/LLD.md §11.3) so the Activity screen has data from day one.
-- Fixed ids, sequences, keys and timestamps: identical on every machine. Amounts follow the fee
-- formula (fee 500 and VAT 70 for every amountDue ≤ 100 000). usr_02 stays empty on purpose.

INSERT INTO inquiries (id, user_id, service_id, session_id, subscriber_number, customer_name,
                       bill_month, amount_due, service_fee, vat, total, rule, status,
                       failed_pin_attempts, created_at, expires_at)
VALUES
    ('inq_seed_5500', 'usr_01', 'svc_elec_cairo',       NULL, '1024750891',  'Mina A.',    '2026-08', 24750, 500, 70, 25320, 'NORMAL',  'CONFIRMED', 0, '2026-09-20T16:30:00Z', '2026-09-20T16:35:00Z'),
    ('inq_seed_5501', 'usr_01', 'svc_gas_town',         NULL, '12088008',    'Mina A.',    '2026-08',  8800, 500, 70,  9370, 'PENDING', 'CONFIRMED', 0, '2026-09-20T16:03:00Z', '2026-09-20T16:08:00Z'),
    ('inq_seed_5502', 'usr_01', 'svc_water_cairo',      NULL, '101426007',   'Fatma H.',   '2026-08', 14260, 500, 70, 14830, 'DECLINE', 'OPEN',      0, '2026-09-20T14:47:00Z', '2026-09-20T14:52:00Z'),
    ('inq_seed_5503', 'usr_01', 'svc_net_we',           NULL, '0231000001',  'Mina A.',    '2026-08', 31000, 500, 70, 31570, 'NORMAL',  'CONFIRMED', 0, '2026-09-18T06:11:00Z', '2026-09-18T06:16:00Z'),
    ('inq_seed_5504', 'usr_01', 'svc_mob_vodafone',     NULL, '01050000002', 'Heba F.',    '2026-08',  5000, 500, 70,  5570, 'NORMAL',  'CONFIRMED', 0, '2026-09-17T10:59:00Z', '2026-09-17T11:04:00Z'),
    ('inq_seed_5505', 'usr_01', 'svc_elec_north_delta', NULL, '1012345673',  'Mohamed K.', '2026-08', 12345, 500, 70, 12915, 'NORMAL',  'CONFIRMED', 0, '2026-09-10T08:29:00Z', '2026-09-10T08:34:00Z');

INSERT INTO transactions (id, seq, user_id, inquiry_id, service_id, idempotency_key, status,
                          failure_code, reference, subscriber_number, customer_name, bill_month,
                          amount_due, service_fee, vat, total, created_at, paid_at, pending_until)
VALUES
    ('txn_5500', 5500, 'usr_01', 'inq_seed_5500', 'svc_elec_cairo',       '00000000-0000-4000-8000-000000005500', 'SUCCESS', NULL,                   'MP-20260920-5500', '1024750891',  'Mina A.',    '2026-08', 24750, 500, 70, 25320, '2026-09-20T16:31:00Z', '2026-09-20T16:31:00Z', NULL),
    ('txn_5501', 5501, 'usr_01', 'inq_seed_5501', 'svc_gas_town',         '00000000-0000-4000-8000-000000005501', 'SUCCESS', NULL,                   'MP-20260920-5501', '12088008',    'Mina A.',    '2026-08',  8800, 500, 70,  9370, '2026-09-20T16:04:00Z', '2026-09-20T16:04:10Z', '2026-09-20T16:04:10Z'),
    ('txn_5502', 5502, 'usr_01', 'inq_seed_5502', 'svc_water_cairo',      '00000000-0000-4000-8000-000000005502', 'FAILED',  'INSUFFICIENT_BALANCE', 'MP-20260920-5502', '101426007',   'Fatma H.',   '2026-08', 14260, 500, 70, 14830, '2026-09-20T14:48:00Z', NULL,                   NULL),
    ('txn_5503', 5503, 'usr_01', 'inq_seed_5503', 'svc_net_we',           '00000000-0000-4000-8000-000000005503', 'SUCCESS', NULL,                   'MP-20260918-5503', '0231000001',  'Mina A.',    '2026-08', 31000, 500, 70, 31570, '2026-09-18T06:12:00Z', '2026-09-18T06:12:00Z', NULL),
    ('txn_5504', 5504, 'usr_01', 'inq_seed_5504', 'svc_mob_vodafone',     '00000000-0000-4000-8000-000000005504', 'SUCCESS', NULL,                   'MP-20260917-5504', '01050000002', 'Heba F.',    '2026-08',  5000, 500, 70,  5570, '2026-09-17T11:00:00Z', '2026-09-17T11:00:00Z', NULL),
    ('txn_5505', 5505, 'usr_01', 'inq_seed_5505', 'svc_elec_north_delta', '00000000-0000-4000-8000-000000005505', 'SUCCESS', NULL,                   'MP-20260910-5505', '1012345673',  'Mohamed K.', '2026-08', 12345, 500, 70, 12915, '2026-09-10T08:30:00Z', '2026-09-10T08:30:00Z', NULL);

-- new transactions continue after the seeded ones
SELECT setval('transaction_seq', 5505);
