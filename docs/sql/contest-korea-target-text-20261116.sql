-- Source: ContestKorea.csv target_text, 2026-11-16. Existing CONTEST_KOREA rows only.
-- Keep the application stopped. Execute in H2 Shell, not bash.
-- On ANY error: ROLLBACK; Do not COMMIT.
SET AUTOCOMMIT FALSE;
SELECT COUNT(*) AS announcements_before FROM announcements;
SELECT announcement_id, external_id, title, enrollment_requirement FROM announcements WHERE source_provider = 'CONTEST_KOREA' AND external_id IN ('202609150064','202610260051','202610200007','202610130040','202610190032','202611050035','202610280013','202610140026','202610060040','202611020040') ORDER BY external_id;
UPDATE announcements SET enrollment_requirement = '누구나 (언론사·지자체·교육단체·환경단체 등)' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202609150064';
UPDATE announcements SET enrollment_requirement = '중학생·고등학생·대학생·일반인 (만 14세 이상 대한민국 거주자)' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202610260051';
UPDATE announcements SET enrollment_requirement = '대학생·대학원생·일반인 (대구·경북 소재 학생 및 졸업 3년 이내인 자)' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202610200007';
UPDATE announcements SET enrollment_requirement = '초등학생·중학생·고등학생·대학생·대학원생 (서울 지역 어린이·청소년(만 9~24세))' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202610130040';
UPDATE announcements SET enrollment_requirement = '일반인 (청년(만 19~39세))' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202610190032';
UPDATE announcements SET enrollment_requirement = '성남시 거주·재직·재학·활동 청년(만 19~39세) 3~6명으로 구성된 팀' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202611050035';
UPDATE announcements SET enrollment_requirement = '일반인 (만 19~39세)' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202610280013';
UPDATE announcements SET enrollment_requirement = '대학생·대학원생·일반인 (행정학·정책학 등 관련 전공)' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202610140026';
UPDATE announcements SET enrollment_requirement = '대학생 (전국 간호대학 재·휴학생)' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202610060040';
UPDATE announcements SET enrollment_requirement = '초등학생·중학생·고등학생·대학생 (대학 재적(재학·휴학·학사유예) 중이며 만 30세 이하인 학생(대학생 IT논문경진대회 기준))' WHERE source_provider = 'CONTEST_KOREA' AND external_id = '202611020040';
SELECT announcement_id, external_id, title, enrollment_requirement FROM announcements WHERE source_provider = 'CONTEST_KOREA' AND external_id IN ('202609150064','202610260051','202610200007','202610130040','202610190032','202611050035','202610280013','202610140026','202610060040','202611020040') ORDER BY external_id;
SELECT COUNT(*) AS announcements_after FROM announcements;
-- No COMMIT here. Check the results first, then type COMMIT; or ROLLBACK; manually.
