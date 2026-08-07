-- Resume Intelligence Caching Table
CREATE TABLE resume_intelligence_cache (
    checksum_sha256 VARCHAR(64) PRIMARY KEY,
    parsed_text TEXT NOT NULL,
    structured_knowledge JSONB NOT NULL,
    quality_metrics JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Resume Validation Reports Table
CREATE TABLE resume_validation_reports (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES ai_documents(id) ON DELETE CASCADE,
    has_email BOOLEAN NOT NULL DEFAULT FALSE,
    has_phone BOOLEAN NOT NULL DEFAULT FALSE,
    has_linkedin BOOLEAN NOT NULL DEFAULT FALSE,
    validation_warnings JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_resume_validation_reports_doc ON resume_validation_reports(document_id);
