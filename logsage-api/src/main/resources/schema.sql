CREATE TABLE IF NOT EXISTS entries (id VARCHAR(36) PRIMARY KEY, batch_id VARCHAR(36), event_time VARCHAR(50), service VARCHAR(100), level VARCHAR(12), logger VARCHAR(255), message CLOB, stack CLOB, incident_id VARCHAR(64));
CREATE TABLE IF NOT EXISTS incidents (id VARCHAR(64) PRIMARY KEY, title VARCHAR(255), service VARCHAR(100), exception_type VARCHAR(255), severity VARCHAR(12), status VARCHAR(20) DEFAULT 'OPEN');
CREATE TABLE IF NOT EXISTS analyses (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(64), payload CLOB, created_at VARCHAR(50));
CREATE TABLE IF NOT EXISTS feedback (id VARCHAR(36) PRIMARY KEY, analysis_id VARCHAR(36), rating VARCHAR(20), created_at VARCHAR(50));
