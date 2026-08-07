CREATE TABLE classes (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    opened_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_classes_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT uk_classes_code UNIQUE (code),
    CONSTRAINT chk_classes_code_uppercase CHECK (code = UPPER(code)),
    CONSTRAINT chk_classes_status CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_classes_course_id ON classes (course_id);
CREATE INDEX idx_classes_status ON classes (status);
