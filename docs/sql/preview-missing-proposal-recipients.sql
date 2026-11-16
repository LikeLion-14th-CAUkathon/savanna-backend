-- Read-only. No writes and no transaction-setting changes.
SELECT p.proposal_id, e.expedition_id, e.team_id,
       m.user_id, a.title, a.deadline
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
  )
ORDER BY e.team_id, p.proposal_id, m.user_id;
