CREATE TABLE modules (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    sequence_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_modules_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT uk_modules_course_code UNIQUE (course_id, code),
    CONSTRAINT uk_modules_course_sequence UNIQUE (course_id, sequence_number),
    CONSTRAINT chk_modules_code_uppercase CHECK (code = UPPER(code)),
    CONSTRAINT chk_modules_sequence_positive CHECK (sequence_number > 0),
    CONSTRAINT chk_modules_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_modules_course_id ON modules (course_id);
CREATE INDEX idx_modules_course_sequence ON modules (course_id, sequence_number);
CREATE INDEX idx_modules_status ON modules (status);
