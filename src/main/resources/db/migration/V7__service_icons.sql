-- The icons from V2 (cdn.momknpay.local) were never hosted. The backend now serves them itself
-- from src/main/resources/static/icons/; an inactive service gets the grey variant.
-- The updated_at trigger stamps every row, so clients pick the new URLs up on their next sync.

UPDATE services
SET icon_url = 'https://momkn.ahmedsayed.net/icons/icon-' || category
               || CASE WHEN is_active THEN '-active' ELSE '-inactive' END || '.png';
