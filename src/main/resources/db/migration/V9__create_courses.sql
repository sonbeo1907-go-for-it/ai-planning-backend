CREATE TABLE courses (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_courses_code UNIQUE (code),
    CONSTRAINT chk_courses_code_uppercase CHECK (code = UPPER(code)),
    CONSTRAINT chk_courses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_courses_status ON courses (status);
CREATE INDEX idx_courses_name ON courses (name);
