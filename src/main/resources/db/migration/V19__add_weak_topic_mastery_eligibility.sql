ALTER TABLE weak_topics ADD COLUMN eligibility_zone VARCHAR(50);
ALTER TABLE weak_topics ADD COLUMN eligible_on DATE;
ALTER TABLE weak_topics ADD COLUMN last_mastery_score NUMERIC(5, 2);

-- Existing rows have no historical timezone snapshot. Use the currently saved
-- profile timezone for this one-time backfill, falling back to UTC.
UPDATE weak_topics weak
SET eligibility_zone = COALESCE(
        (SELECT profile.time_zone
         FROM user_profiles profile
         WHERE profile.user_id = weak.user_id),
        'UTC'
    );

UPDATE weak_topics
SET eligible_on = (unresolved_at AT TIME ZONE eligibility_zone)::date + 1;

ALTER TABLE weak_topics ALTER COLUMN eligibility_zone SET NOT NULL;
ALTER TABLE weak_topics ALTER COLUMN eligible_on SET NOT NULL;

ALTER TABLE weak_topics
    ADD CONSTRAINT ck_weak_topics_mastery_score
        CHECK (last_mastery_score IS NULL OR last_mastery_score BETWEEN 0 AND 100);
