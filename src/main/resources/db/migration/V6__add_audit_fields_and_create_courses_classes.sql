-- Add created_by and updated_by to existing tables
ALTER TABLE user_accounts ADD COLUMN created_by UUID;
ALTER TABLE user_accounts ADD COLUMN updated_by UUID;

ALTER TABLE auth_sessions ADD COLUMN created_by UUID;
ALTER TABLE auth_sessions ADD COLUMN updated_by UUID;

ALTER TABLE refresh_tokens ADD COLUMN created_by UUID;
ALTER TABLE refresh_tokens ADD COLUMN updated_by UUID;

ALTER TABLE audit_logs ADD COLUMN created_by UUID;
ALTER TABLE audit_logs ADD COLUMN updated_by UUID;

-- Create courses table
CREATE TABLE courses (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    
    CONSTRAINT uk_courses_code UNIQUE (code),
    CONSTRAINT ck_courses_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_courses_status ON courses (status);

-- Create classes table
CREATE TABLE classes (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    
    course_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT fk_classes_course_id FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT uk_classes_code UNIQUE (code),
    CONSTRAINT ck_classes_status
        CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_classes_course_id ON classes (course_id);
CREATE INDEX idx_classes_status ON classes (status);
