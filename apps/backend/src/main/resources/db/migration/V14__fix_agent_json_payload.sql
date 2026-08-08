-- V14: Change payload/result columns from jsonb to text (to fix Hibernate double-encoding bug)
-- and repair any existing double-encoded string values

-- Convert jsonb columns to text in agent_tasks, unwrapping string-encoded JSON
ALTER TABLE agent_tasks
    ALTER COLUMN payload TYPE text USING
        CASE
            WHEN payload IS NULL THEN '{}'
            WHEN jsonb_typeof(payload) = 'string' THEN payload #>> '{}'
            ELSE payload::text
        END;

ALTER TABLE agent_tasks
    ALTER COLUMN result TYPE text USING
        CASE
            WHEN result IS NULL THEN NULL
            WHEN jsonb_typeof(result) = 'string' THEN result #>> '{}'
            ELSE result::text
        END;

-- Convert jsonb columns to text in agent_executions
ALTER TABLE agent_executions
    ALTER COLUMN payload TYPE text USING
        CASE
            WHEN payload IS NULL THEN '{}'
            WHEN jsonb_typeof(payload) = 'string' THEN payload #>> '{}'
            ELSE payload::text
        END;

ALTER TABLE agent_executions
    ALTER COLUMN result TYPE text USING
        CASE
            WHEN result IS NULL THEN NULL
            WHEN jsonb_typeof(result) = 'string' THEN result #>> '{}'
            ELSE result::text
        END;
