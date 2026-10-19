-- Home-screen ads (FR-AD, LLD §11.6): an image served from static/ads/ and an https link that
-- the app opens in the browser. No admin screen: ads are added and changed by migrations.

CREATE TABLE ads (
    id          VARCHAR(32)  PRIMARY KEY,
    title_en    VARCHAR(120) NOT NULL,
    title_ar    VARCHAR(120) NOT NULL,
    image_url   VARCHAR(255) NOT NULL,
    link_url    VARCHAR(500) NOT NULL
                CONSTRAINT ck_ads_link_https CHECK (link_url LIKE 'https://%'),  -- the app opens it as is
    sort_order  SMALLINT     NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO ads (id, title_en, title_ar, image_url, link_url, sort_order) VALUES
    ('ad_nbe',      'National Bank of Egypt: accounts, cards and digital banking', 'البنك الأهلي المصري: حسابات وبطاقات وخدمات رقمية', 'https://momkn.ahmedsayed.net/ads/ad-nbe.png',      'https://www.nbe.com.eg',      1),
    ('ad_vodafone', 'Recharge and pay your Vodafone line in seconds',              'اشحن وادفع خط فودافون في ثوانٍ',                  'https://momkn.ahmedsayed.net/ads/ad-vodafone.png', 'https://web.vodafone.com.eg', 2),
    ('ad_eand',     'Top up your e& line anytime, anywhere',                       'اشحن خط اتصالات من أي مكان وفي أي وقت',           'https://momkn.ahmedsayed.net/ads/ad-eand.png',     'https://www.etisalat.eg',     3),
    ('ad_orange',   'Top up Orange and pay your home internet',                    'اشحن أورنج وادفع إنترنت المنزل',                  'https://momkn.ahmedsayed.net/ads/ad-orange.png',   'https://www.orange.eg',       4),
    ('ad_we',       'Pay your WE landline and internet in one tap',                'ادفع الخط الأرضي وإنترنت وي بلمسة واحدة',          'https://momkn.ahmedsayed.net/ads/ad-we.png',       'https://te.eg',               5);
