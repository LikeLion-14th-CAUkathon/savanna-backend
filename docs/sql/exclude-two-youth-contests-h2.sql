-- H2 only. Normally stop the app and back up the real DB before running.
-- Source + external IDs, NOT environment-dependent announcement_id or broad title matching.
-- 202611040033: 어린이 미래 상상 그림 공모전
-- 202610170003: 2026 ACEL 전국 청소년 미술 공모전
SET AUTOCOMMIT FALSE;

INSERT INTO announcement_source_events
    (announcement_id, external_id, source, event_type, details, recorded_at)
SELECT announcement_id, external_id, 'MANUAL', 'MANUAL_EXCLUSION',
       '{"reason":"[MANUAL_REQUEST] 사용자 지정 제외: 어린이/청소년 미술 공모전 2건"}', CURRENT_TIMESTAMP
FROM announcements
WHERE source_provider = 'CONTEST_KOREA'
  AND external_id IN ('202611040033', '202610170003')
  AND (visibility = TRUE OR exclude_reason IS NULL
       OR LOCATE('[MANUAL_REQUEST] 사용자 지정 제외: 어린이/청소년 미술 공모전 2건', exclude_reason) = 0);

UPDATE announcements
SET visibility = FALSE,
    exclude_reason = CASE
        WHEN exclude_reason IS NULL OR TRIM(exclude_reason) = ''
            THEN '[MANUAL_REQUEST] 사용자 지정 제외: 어린이/청소년 미술 공모전 2건'
        WHEN LOCATE('[MANUAL_REQUEST] 사용자 지정 제외: 어린이/청소년 미술 공모전 2건', exclude_reason) > 0
            THEN exclude_reason
        ELSE exclude_reason || CHAR(10) || '[MANUAL_REQUEST] 사용자 지정 제외: 어린이/청소년 미술 공모전 2건'
    END
WHERE source_provider = 'CONTEST_KOREA'
  AND external_id IN ('202611040033', '202610170003');

COMMIT;

SELECT announcement_id, external_id, title, visibility, exclude_reason
FROM announcements
WHERE source_provider = 'CONTEST_KOREA'
  AND external_id IN ('202611040033', '202610170003');

-- Must be 0. A match count below 2 in the SELECT above means a missing/unmatched row.
SELECT COUNT(*) AS remaining_public
FROM announcements
WHERE source_provider = 'CONTEST_KOREA'
  AND external_id IN ('202611040033', '202610170003')
  AND visibility = TRUE AND exclude_reason IS NULL AND classification_issue IS NULL;
