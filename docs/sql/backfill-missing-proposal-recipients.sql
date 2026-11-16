-- One-time maintenance. Stop app writers and back up the existing DB first.
-- Paste directly into H2 Shell after SET AUTOCOMMIT FALSE; (once).
-- Do not use SQL RUNSCRIPT. On any error, ROLLBACK; in the same session.
-- No automatic COMMIT, no updates/deletes of existing recipients.
INSERT INTO proposal_recipients (
    proposal_id, user_id, status, read_at, responded_at,
    response_version, response_read_version, response_read_at
)
SELECT p.proposal_id, m.user_id, 'PENDING', NULL, NULL, 0, 0, NULL
FROM proposals p
JOIN expeditions e ON e.expedition_id = p.expedition_id
JOIN announcements a ON a.announcement_id = e.announcement_id
JOIN team_member m ON m.team_id = e.team_id
WHERE a.deadline >= CAST(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul' AS DATE)
  AND e.submission_completed_at IS NULL
  AND e.stage <> 'SUBMITTED'
  AND m.user_id <> p.sender_id
  AND NOT EXISTS (
      SELECT 1 FROM proposal_recipients r
      WHERE r.proposal_id = p.proposal_id AND r.user_id = m.user_id
  );
