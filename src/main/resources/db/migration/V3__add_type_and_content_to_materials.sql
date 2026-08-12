-- Thêm cột type, status, content
ALTER TABLE materials ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'FILE';
ALTER TABLE materials ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'READY';
ALTER TABLE materials ADD COLUMN content TEXT;

-- Đổi các cột liên quan đến file thành cho phép NULL
ALTER TABLE materials ALTER COLUMN original_file_name DROP NOT NULL;
ALTER TABLE materials ALTER COLUMN content_type DROP NOT NULL;
ALTER TABLE materials ALTER COLUMN file_size DROP NOT NULL;
ALTER TABLE materials ALTER COLUMN storage_key DROP NOT NULL;

-- Thêm các ràng buộc toàn vẹn dữ liệu
-- 1. Status chỉ nhận 1 trong 3 giá trị PENDING, READY, FAILED
ALTER TABLE materials
ADD CONSTRAINT chk_materials_status CHECK (status IN ('PENDING', 'READY', 'FAILED'));

-- 2. Ràng buộc trường dữ liệu tùy theo type (TEXT/GOAL_DESCRIPTION thì bắt buộc có content và không có file info)
ALTER TABLE materials
ADD CONSTRAINT chk_materials_type_content CHECK (
    (type = 'FILE' AND storage_key IS NOT NULL AND original_file_name IS NOT NULL AND content_type IS NOT NULL AND file_size IS NOT NULL AND content IS NULL) OR
    (type IN ('TEXT', 'GOAL_DESCRIPTION') AND content IS NOT NULL AND storage_key IS NULL AND original_file_name IS NULL AND content_type IS NULL AND file_size IS NULL)
);
