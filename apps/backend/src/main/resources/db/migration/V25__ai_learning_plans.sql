-- AI learning plans built from real skill gaps (no templated steps, no invented match-improvement numbers).
ALTER TABLE learning_paths ADD COLUMN IF NOT EXISTS why_it_matters TEXT;
ALTER TABLE learning_paths ADD COLUMN IF NOT EXISTS practice_project TEXT;
ALTER TABLE learning_paths ADD COLUMN IF NOT EXISTS demand_evidence TEXT;
ALTER TABLE learning_paths ADD COLUMN IF NOT EXISTS ai_model VARCHAR(100);

ALTER TABLE learning_path_items ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE learning_path_items ADD COLUMN IF NOT EXISTS estimated_hours DOUBLE PRECISION;
ALTER TABLE learning_path_items ADD COLUMN IF NOT EXISTS resources_json TEXT;
