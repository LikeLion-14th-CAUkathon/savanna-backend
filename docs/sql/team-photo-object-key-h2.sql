-- Stop the app normally and back up the existing H2 file before applying.
ALTER TABLE team ADD COLUMN IF NOT EXISTS photo_url VARCHAR(2048);
ALTER TABLE team ADD COLUMN IF NOT EXISTS photo_object_key VARCHAR(512);
-- Audit legacy values without printing URLs (which may contain credentials).
SELECT team_id FROM team WHERE photo_url LIKE '%?%' OR photo_url LIKE 'blob:%' OR photo_url LIKE 'data:%';
-- Do not convert an arbitrary URL to an S3 key. Review/re-upload legacy photos.
