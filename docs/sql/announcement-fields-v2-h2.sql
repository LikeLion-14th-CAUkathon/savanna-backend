-- H2 전용 참고 SQL. 앱 중지/백업 필수. 권장: importContestKorea -PcontestKoreaMigrate=true
-- 먼저 아래 SELECT의 결과 DDL을 실행하여 기존 field CHECK를 제거한다.
SELECT 'ALTER TABLE announcements DROP CONSTRAINT "' || REPLACE(t.CONSTRAINT_NAME,'"','""') || '";'
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS t
JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS c ON t.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=c.CONSTRAINT_NAME
WHERE t.TABLE_NAME='ANNOUNCEMENTS' AND t.CONSTRAINT_TYPE='CHECK' AND c.CHECK_CLAUSE LIKE '%"FIELD"%';

-- 위 결과 실행 후 아래 진행. 원본 enum·근거는 UPDATE 전에 보존한다.
ALTER TABLE announcements ALTER COLUMN field VARCHAR(50);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS classification_issue VARCHAR(255);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS exclude_reason TEXT;
CREATE TABLE IF NOT EXISTS announcement_field_migration_snapshot AS
SELECT announcement_id, external_id, field AS previous_field, external_category, source_url FROM announcements;

-- 제목/ESG 키워드로 분류하지 않는다. category_source CSV를 함께 사용하는 경우 JDBC 경로 권장.
UPDATE announcements SET field=CASE
 WHEN external_category IN ('학문•과학•IT','학문·과학·IT') THEN 'ACADEMIC_SCIENCE_IT'
 WHEN external_category IN ('아이디어•건축•창업','아이디어·건축·창업') THEN 'IDEA_ARCHITECTURE_STARTUP'
 WHEN external_category IN ('미술•디자인•웹툰','미술·디자인·웹툰') THEN 'ART_DESIGN_WEBTOON'
 WHEN field='DEVELOPMENT_HACKATHON' THEN 'ACADEMIC_SCIENCE_IT'
 WHEN field='IDEA_PLANNING' THEN 'IDEA_ARCHITECTURE_STARTUP'
 WHEN field='DESIGN' THEN 'ART_DESIGN_WEBTOON'
 ELSE field END;
-- 근거가 아직 확인되지 않은 SOCIAL_ESG는 보류. URL/CSV 근거를 검토한 뒤 개별 확정한다.
UPDATE announcements SET field=NULL,classification_issue='UNVERIFIED_LEGACY_CATEGORY' WHERE field='SOCIAL_ESG';
ALTER TABLE announcements ADD CONSTRAINT ck_announcement_field_v2 CHECK(field IS NULL OR field IN ('ACADEMIC_SCIENCE_IT','IDEA_ARCHITECTURE_STARTUP','ART_DESIGN_WEBTOON'));
UPDATE announcements SET theme=NULL WHERE TRIM(theme) IN ('원문 참고','원문참고','원문 확인','공고 원문을 확인해 주세요');
UPDATE announcements SET evaluation_criteria=NULL WHERE TRIM(evaluation_criteria) IN ('원문 참고','원문참고','원문 확인','공고 원문을 확인해 주세요');
UPDATE announcements SET submission_requirements=NULL WHERE TRIM(submission_requirements) IN ('원문 참고','원문참고','원문 확인','공고 원문을 확인해 주세요');
UPDATE announcements SET enrollment_requirement=NULL WHERE TRIM(enrollment_requirement) IN ('원문 참고','원문참고','원문 확인','공고 원문을 확인해 주세요');
-- 다음 단계: 새 앱 스키마 확장, ALL 수집, CSV 보완, 분야 필터 검증.
