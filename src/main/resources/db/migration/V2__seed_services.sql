-- Seed catalogue (docs/LLD.md §11.1): 24 visible services across all six categories plus one
-- soft-deleted row that shows up in /services/sync deletedIds. Fixed timestamps so every machine
-- has identical data. Arabic names are stored without diacritics.

INSERT INTO services (id, name_en, name_ar, category, icon_url, input_label, input_pattern,
                      min_amount, max_amount, is_active, created_at, updated_at, deleted_at)
VALUES
    -- electricity
    ('svc_elec_cairo',       'Cairo Electricity',       'كهرباء القاهرة',      'electricity', 'https://cdn.momknpay.local/icons/electricity.png', 'Subscriber number', '^[0-9]{10}$', 500, 500000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_elec_alex',        'Alexandria Electricity',  'كهرباء الإسكندرية',   'electricity', 'https://cdn.momknpay.local/icons/electricity.png', 'Subscriber number', '^[0-9]{10}$', 500, 500000, FALSE, '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_elec_north_delta', 'North Delta Electricity', 'كهرباء شمال الدلتا',  'electricity', 'https://cdn.momknpay.local/icons/electricity.png', 'Subscriber number', '^[0-9]{10}$', 500, 500000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_elec_south_delta', 'South Delta Electricity', 'كهرباء جنوب الدلتا',  'electricity', 'https://cdn.momknpay.local/icons/electricity.png', 'Subscriber number', '^[0-9]{10}$', 500, 500000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_elec_canal_slow',  'Canal Electricity',       'كهرباء القناة',       'electricity', 'https://cdn.momknpay.local/icons/electricity.png', 'Subscriber number', '^[0-9]{10}$', 500, 500000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    -- water
    ('svc_water_cairo',      'Greater Cairo Water',     'مياه القاهرة الكبرى', 'water',       'https://cdn.momknpay.local/icons/water.png',       'Account number',    '^[0-9]{9}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_water_alex',       'Alexandria Water',        'مياه الإسكندرية',     'water',       'https://cdn.momknpay.local/icons/water.png',       'Account number',    '^[0-9]{9}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_water_giza',       'Giza Water',              'مياه الجيزة',         'water',       'https://cdn.momknpay.local/icons/water.png',       'Account number',    '^[0-9]{9}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_water_dakahlia',   'Dakahlia Water',          'مياه الدقهلية',       'water',       'https://cdn.momknpay.local/icons/water.png',       'Account number',    '^[0-9]{9}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    -- gas
    ('svc_gas_town',         'Town Gas',                'غاز المدن',           'gas',         'https://cdn.momknpay.local/icons/gas.png',         'Customer number',   '^[0-9]{8}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_gas_petrotrade',   'Petrotrade Gas',          'بتروتريد للغاز',      'gas',         'https://cdn.momknpay.local/icons/gas.png',         'Customer number',   '^[0-9]{8}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_gas_egypt',        'Egypt Gas',               'غاز مصر',             'gas',         'https://cdn.momknpay.local/icons/gas.png',         'Customer number',   '^[0-9]{8}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_gas_natgas_slow',  'Natgas',                  'ناتجاس',              'gas',         'https://cdn.momknpay.local/icons/gas.png',         'Customer number',   '^[0-9]{8}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    -- internet
    ('svc_net_we',           'WE Internet',             'وي إنترنت',           'internet',    'https://cdn.momknpay.local/icons/internet.png',    'Landline number',   '^0[2-9][0-9]{7,8}$', 1000, 500000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_net_orange',       'Orange Home Internet',    'أورنج إنترنت منزلي',  'internet',    'https://cdn.momknpay.local/icons/internet.png',    'Landline number',   '^0[2-9][0-9]{7,8}$', 1000, 500000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_net_vodafone',     'Vodafone Home Internet',  'فودافون إنترنت منزلي', 'internet',   'https://cdn.momknpay.local/icons/internet.png',    'Landline number',   '^0[2-9][0-9]{7,8}$', 1000, 500000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_net_etisalat',     'e& Home Internet',        'اتصالات إنترنت منزلي', 'internet',   'https://cdn.momknpay.local/icons/internet.png',    'Landline number',   '^0[2-9][0-9]{7,8}$', 1000, 500000, FALSE, '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    -- mobile top-up
    ('svc_mob_vodafone',     'Vodafone Top-up',         'شحن فودافون',         'mobile',      'https://cdn.momknpay.local/icons/mobile.png',      'Mobile number',     '^010[0-9]{8}$', 500, 200000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_mob_etisalat',     'e& Top-up',               'شحن اتصالات',         'mobile',      'https://cdn.momknpay.local/icons/mobile.png',      'Mobile number',     '^011[0-9]{8}$', 500, 200000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_mob_orange',       'Orange Top-up',           'شحن أورنج',           'mobile',      'https://cdn.momknpay.local/icons/mobile.png',      'Mobile number',     '^012[0-9]{8}$', 500, 200000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_mob_we',           'WE Top-up',               'شحن وي',              'mobile',      'https://cdn.momknpay.local/icons/mobile.png',      'Mobile number',     '^015[0-9]{8}$', 500, 200000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    -- landline
    ('svc_land_we',          'WE Landline',             'الخط الأرضي وي',      'landline',    'https://cdn.momknpay.local/icons/landline.png',    'Landline number',   '^0[2-9][0-9]{7,8}$', 500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_land_we_business', 'WE Business Landline',    'الخط الأرضي وي للأعمال', 'landline', 'https://cdn.momknpay.local/icons/landline.png',    'Landline number',   '^0[2-9][0-9]{7,8}$', 500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    ('svc_land_prepaid',     'WE Prepaid Landline',     'الخط الأرضي المدفوع مقدما', 'landline', 'https://cdn.momknpay.local/icons/landline.png', 'Landline number',   '^0[2-9][0-9]{7,8}$', 500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-18T09:00:00Z', NULL),
    -- soft-deleted: appears only in /services/sync deletedIds
    ('svc_water_legacy',     'Legacy Water Board',      'هيئة المياه القديمة', 'water',       'https://cdn.momknpay.local/icons/water.png',       'Account number',    '^[0-9]{9}$',  500, 300000, TRUE,  '2026-09-18T09:00:00Z', '2026-09-19T09:00:00Z', '2026-09-19T09:00:00Z');
