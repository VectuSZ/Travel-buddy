UPDATE trips SET status='OPEN', published_at=CASE WHEN content_language='pl' THEN TIMESTAMPTZ '2026-01-02 00:00:00Z' ELSE TIMESTAMPTZ '2026-01-01 00:00:00Z' END,
 looking_for='Travel companions', target_people=4, budget_min=0,budget_max=1000,styles=ARRAY['trekking']
WHERE organizer_id='33333333-3333-4333-8333-333333333333';
