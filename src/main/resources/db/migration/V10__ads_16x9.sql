-- The ad banners are 16:9 now (1280x720). New file names, so a cached 3:1 banner is never shown.

UPDATE ads
SET image_url = replace(image_url, '.png', '-16x9.png')
WHERE image_url LIKE 'https://momkn.ahmedsayed.net/ads/%' AND image_url NOT LIKE '%-16x9.png';
