-- Run the duplicate audit FIRST, while the service is normally stopped and after DB backup.
SELECT user_id, COUNT(*) AS membership_count FROM team_member GROUP BY user_id HAVING COUNT(*) > 1;
-- If any rows are returned, STOP. Decide with owners which membership to retain.
-- Do not delete memberships, teams, invitations, expeditions or responses automatically.
-- The following constraint fails safely if duplicate memberships remain.
ALTER TABLE team_member ADD CONSTRAINT IF NOT EXISTS uk_team_member_user UNIQUE(user_id);
