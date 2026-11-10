-- Keep historical package/rate references and enforce one active normalized price slot.
ALTER TABLE ai_provider_configs ADD COLUMN pricing_category VARCHAR(50);
ALTER TABLE ai_credit_rates ADD COLUMN active_slot VARCHAR(110);

-- Existing duplicate slots deliberately fail the unique constraint rather than silently repricing.
UPDATE ai_credit_rates SET model_category = NULLIF(UPPER(TRIM(model_category)), '');
UPDATE ai_credit_rates SET active_slot = purpose || ':' || COALESCE(model_category, '')
WHERE status = 'ACTIVE';
ALTER TABLE ai_credit_rates ADD CONSTRAINT uk_ai_credit_rates_active_slot UNIQUE (active_slot);
ALTER TABLE ai_credit_rates ADD CONSTRAINT ck_ai_credit_rates_active_slot CHECK (
    (status = 'ARCHIVED' AND active_slot IS NULL) OR
    (status = 'ACTIVE' AND active_slot IS NOT NULL
        AND active_slot = purpose || ':' || COALESCE(model_category, ''))
);

