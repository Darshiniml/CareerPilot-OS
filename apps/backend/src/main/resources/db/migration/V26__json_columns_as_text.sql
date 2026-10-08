-- The JPA entities map these columns as plain String (no JSON JDBC type), which PostgreSQL rejects
-- for jsonb columns ("column is of type jsonb but expression is of type character varying").
-- Same fix as V14 for the agent tables: store the JSON documents as text.

ALTER TABLE agent_events ALTER COLUMN payload TYPE text USING payload::text;
ALTER TABLE ai_document_chunks ALTER COLUMN metadata TYPE text USING metadata::text;
ALTER TABLE ai_documents ALTER COLUMN flexible_metadata TYPE text USING flexible_metadata::text;
ALTER TABLE ai_documents ALTER COLUMN structured_metadata TYPE text USING structured_metadata::text;
ALTER TABLE ai_tasks ALTER COLUMN metadata TYPE text USING metadata::text;
ALTER TABLE ai_tasks ALTER COLUMN payload TYPE text USING payload::text;
ALTER TABLE company_intelligence_cache ALTER COLUMN insights TYPE text USING insights::text;
ALTER TABLE company_intelligence_cache ALTER COLUMN metadata TYPE text USING metadata::text;
ALTER TABLE company_intelligence_cache ALTER COLUMN structured_knowledge TYPE text USING structured_knowledge::text;
ALTER TABLE job_intelligence_cache ALTER COLUMN insights TYPE text USING insights::text;
ALTER TABLE job_intelligence_cache ALTER COLUMN metadata TYPE text USING metadata::text;
ALTER TABLE job_intelligence_cache ALTER COLUMN quality_metrics TYPE text USING quality_metrics::text;
ALTER TABLE job_intelligence_cache ALTER COLUMN structured_knowledge TYPE text USING structured_knowledge::text;
ALTER TABLE resume_intelligence_cache ALTER COLUMN quality_metrics TYPE text USING quality_metrics::text;
ALTER TABLE resume_intelligence_cache ALTER COLUMN structured_knowledge TYPE text USING structured_knowledge::text;
ALTER TABLE resume_validation_reports ALTER COLUMN validation_warnings TYPE text USING validation_warnings::text;
