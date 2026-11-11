-- H2 additive migration. Preserve existing proposal responses, read times and team membership.
ALTER TABLE proposal_recipients ADD COLUMN IF NOT EXISTS response_version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE proposal_recipients ADD COLUMN IF NOT EXISTS response_read_version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE proposal_recipients ADD COLUMN IF NOT EXISTS response_read_at TIMESTAMP;
