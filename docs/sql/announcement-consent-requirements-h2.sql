-- H2 전용. 운영 적용 전 서비스 정상 중지 및 DB 백업 필수.
-- 기존 공고/관계 데이터/상세값을 지우거나 예시 동의서를 채우지 않습니다.
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS consent_requirements TEXT;
