-- Company Intelligence Caching Table
CREATE TABLE company_intelligence_cache (
    checksum_sha256 VARCHAR(64) PRIMARY KEY,
    acquired_content TEXT NOT NULL,
    structured_knowledge JSONB NOT NULL,
    metadata JSONB NOT NULL,
    insights JSONB NOT NULL,
    last_indexed_at TIMESTAMP WITH TIME ZONE,
    source_version VARCHAR(20),
    crawl_timestamp TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
