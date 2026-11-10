ALTER TABLE roadmaps
    DROP CONSTRAINT ck_roadmaps_daily_commitment;

ALTER TABLE roadmaps
    ADD CONSTRAINT ck_roadmaps_daily_commitment
        CHECK (
            daily_commitment_minutes IS NULL
            OR (
                daily_commitment_minutes BETWEEN 15 AND 480
                AND MOD(daily_commitment_minutes, 15) = 0
            )
        );
