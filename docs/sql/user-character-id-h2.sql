-- H2 전용. 서비스 정상 중지·기존 DB 백업 후 새 JAR 시작 전에 적용합니다.
-- 기존 사용자의 캐릭터는 미설정(null)으로 보존하며 임의 캐릭터를 일괄 배정하지 않습니다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS character_id INTEGER;
ALTER TABLE users ADD CONSTRAINT IF NOT EXISTS ck_users_character_id
    CHECK (character_id IS NULL OR character_id BETWEEN 1 AND 9);
