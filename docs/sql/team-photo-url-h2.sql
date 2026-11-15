-- H2 전용. 서비스 정상 중지·기존 DB 백업 후 적용합니다.
-- 기존 팀과 선택하지 않은 사진은 그대로 유지하며 null이면 팀 이름 첫 글자를 표시합니다.
ALTER TABLE team ADD COLUMN IF NOT EXISTS photo_url VARCHAR(2048);
