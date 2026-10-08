-- Resume AI processing: explicit failure reason shown to the candidate instead of fabricated output.
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS ai_processing_error VARCHAR(1000);
