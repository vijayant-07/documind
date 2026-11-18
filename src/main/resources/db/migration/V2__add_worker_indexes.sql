CREATE INDEX idx_analysis_jobs_claimable ON analysis_jobs (status, next_attempt_at, created_at);
CREATE INDEX idx_analysis_jobs_stuck ON analysis_jobs (status, updated_at);

