-- psql -v a=UUID_A -v b=UUID_B -v trip=UUID_WYPRAWY -v c=UUID_C
BEGIN READ ONLY;
SET LOCAL search_path TO stage7a_trial;
WITH ids(id) AS (VALUES (:'a'::uuid), (:'b'::uuid))
SELECT label, remaining FROM (
 SELECT 'users A/B' label, count(*) remaining FROM users WHERE id IN (TABLE ids)
 UNION ALL SELECT 'profiles', count(*) FROM traveler_profiles WHERE user_id IN (TABLE ids)
 UNION ALL SELECT 'contacts', count(*) FROM profile_contacts WHERE user_id IN (TABLE ids)
 UNION ALL SELECT 'sessions', count(*) FROM sessions WHERE user_id IN (TABLE ids)
 UNION ALL SELECT 'tokens', count(*) FROM auth_tokens WHERE user_id IN (TABLE ids)
 UNION ALL SELECT 'identities', count(*) FROM auth_identities WHERE user_id IN (TABLE ids)
 UNION ALL SELECT 'applications and messages', count(*) FROM applications WHERE applicant_id IN (TABLE ids) OR trip_id=:'trip'::uuid
 UNION ALL SELECT 'trips', count(*) FROM trips WHERE organizer_id IN (TABLE ids) OR id=:'trip'::uuid
 UNION ALL SELECT 'locations', count(*) FROM trip_locations WHERE trip_id=:'trip'::uuid
 UNION ALL SELECT 'jobs', count(*) FROM deletion_jobs WHERE user_id IN (TABLE ids)
) rows;
SELECT count(*) AS active_c_should_be_one FROM users WHERE id=:'c'::uuid AND status='ACTIVE';
COMMIT;
