ALTER TABLE roadmaps ADD COLUMN title_origin VARCHAR(30);

UPDATE roadmaps
SET title_origin = CASE
    WHEN title IS NULL OR LENGTH(TRIM(title)) = 0 THEN 'FALLBACK'
    WHEN LOWER(TRIM(title)) = LOWER('Lộ trình từ khảo sát') THEN 'FALLBACK'
    ELSE 'USER'
END;

ALTER TABLE roadmaps ALTER COLUMN title_origin SET NOT NULL;

ALTER TABLE roadmaps
    ADD CONSTRAINT ck_roadmaps_title_origin
        CHECK (title_origin IN ('USER', 'GOAL_DERIVED', 'AI_SUGGESTED', 'FALLBACK'));
