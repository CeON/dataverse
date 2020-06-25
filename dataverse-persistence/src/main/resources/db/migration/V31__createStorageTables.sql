CREATE TABLE IF NOT EXISTS workflowartifact (
    id BIGSERIAL PRIMARY KEY,
    datasetversion_id INTEGER REFERENCES datasetversion(id) NOT NULL,
    workflow_execution_step_id INTEGER,
    created_at TIMESTAMP,
    artifact_name VARCHAR,
    encoding VARCHAR(64),
    storage_type VARCHAR(64) NOT NULL,
    location VARCHAR
);

CREATE TABLE IF NOT EXISTS db_storage (
    id BIGINT PRIMARY KEY,
    stored_data BYTEA
);