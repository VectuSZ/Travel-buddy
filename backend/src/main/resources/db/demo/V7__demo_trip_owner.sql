-- This location is loaded exclusively by dev/test. No login identity or password.
INSERT INTO users(id,email,role,status,ui_locale,terms_version,terms_accepted_at,created_at,updated_at)
VALUES ('33333333-3333-4333-8333-333333333333','demo-organizer@example.invalid','USER','ACTIVE','pl','dev-demo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
UPDATE trips SET organizer_id = '33333333-3333-4333-8333-333333333333'
WHERE id IN ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222') AND organizer_id IS NULL;
INSERT INTO trip_locations(id,trip_id,position,country_code)
SELECT id,id,0,CASE id WHEN '11111111-1111-4111-8111-111111111111'::uuid THEN 'PL' ELSE 'PT' END
FROM trips WHERE id IN ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222');
