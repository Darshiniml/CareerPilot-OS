-- AI Documents
CREATE TABLE ai_documents (
    id UUID PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(255),
    mime_type VARCHAR(100),
    language VARCHAR(10) DEFAULT 'en',
    content TEXT,
    structured_metadata JSONB,
    flexible_metadata JSONB,
    version INT NOT NULL DEFAULT 1,
    checksum VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- AI Document Chunks
CREATE TABLE ai_document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES ai_documents(id) ON DELETE CASCADE,
    chunk_number INT NOT NULL,
    text TEXT NOT NULL,
    token_count INT NOT NULL,
    page_number INT,
    section VARCHAR(255),
    heading VARCHAR(255),
    chunk_type VARCHAR(50),
    source_document_version INT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- AI Document Versions
CREATE TABLE ai_document_versions (
    id UUID PRIMARY KEY,
    parent_document_id UUID NOT NULL REFERENCES ai_documents(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    created_by UUID,
    generated_by_ai BOOLEAN NOT NULL DEFAULT FALSE,
    change_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Embedding References (maps chunks to vector storage ids)
CREATE TABLE embedding_references (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES ai_documents(id) ON DELETE CASCADE,
    chunk_id UUID NOT NULL REFERENCES ai_document_chunks(id) ON DELETE CASCADE,
    provider VARCHAR(100) NOT NULL,
    collection VARCHAR(100) NOT NULL,
    vector_id VARCHAR(255) NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    embedding_version VARCHAR(50) NOT NULL,
    vector_dimension INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_ai_documents_owner ON ai_documents(owner_id);
CREATE INDEX idx_ai_documents_status ON ai_documents(status);
CREATE INDEX idx_ai_document_chunks_doc ON ai_document_chunks(document_id);
CREATE INDEX idx_embedding_refs_doc ON embedding_references(document_id);
