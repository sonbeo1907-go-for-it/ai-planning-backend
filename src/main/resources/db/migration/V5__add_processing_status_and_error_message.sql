-- Cập nhật cột status, thêm PROCESSING
ALTER TABLE materials DROP CONSTRAINT chk_materials_status;
ALTER TABLE materials ADD CONSTRAINT chk_materials_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'));

-- Cập nhật chk_materials_type_content để cho phép FILE có content khi đã READY
ALTER TABLE materials DROP CONSTRAINT chk_materials_type_content;
ALTER TABLE materials ADD CONSTRAINT chk_materials_type_content CHECK (
    (type = 'FILE' AND storage_key IS NOT NULL AND original_file_name IS NOT NULL AND content_type IS NOT NULL AND file_size IS NOT NULL AND (
        (status = 'READY' AND content IS NOT NULL) OR 
        (status != 'READY' AND content IS NULL)
    )) OR
    (type IN ('TEXT', 'GOAL_DESCRIPTION') AND content IS NOT NULL AND storage_key IS NULL AND original_file_name IS NULL AND content_type IS NULL AND file_size IS NULL)
);

-- Thêm error_code, error_message và processing_started_at
ALTER TABLE materials ADD COLUMN error_code VARCHAR(50);
ALTER TABLE materials ADD COLUMN error_message TEXT;
ALTER TABLE materials ADD COLUMN processing_started_at TIMESTAMP WITH TIME ZONE;

-- Thêm constraint đảm bảo dữ liệu toàn vẹn khi FAILED
ALTER TABLE materials ADD CONSTRAINT chk_materials_failed_error CHECK (
    (status = 'FAILED' AND error_code IS NOT NULL AND error_message IS NOT NULL) OR
    (status != 'FAILED' AND error_code IS NULL AND error_message IS NULL)
);
