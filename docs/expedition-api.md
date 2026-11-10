# Expedition API

- GET /api/v1/expeditions/{expeditionId}/proposals: 해당 팀원이 제안과 수신자별 응답·집계를 조회합니다. 상세 응답은 [원정 제안 API](expedition-proposals-api.md)를 참고하세요.

- GET /api/v1/expeditions/{expeditionId}: 공고 요약, 단계, 목표일, 제출 완료 여부
- PATCH /api/v1/expeditions/{expeditionId}/stage: {"stage":"DRAFT"} 등. 단계 변경과 이력을 같은 트랜잭션으로 저장합니다. 동일 단계 요청은 이력을 만들지 않습니다.
- GET /api/v1/expeditions/{expeditionId}/stage-histories?page=0&size=10: 최신순 이력. size는 1~100.
- PATCH /api/v1/expeditions/{expeditionId}/schedule: kickoffDate/draftDate/reviewDate/submitTargetDate를 YYYY-MM-DD로 전달합니다. 생략 또는 null은 기존 값을 유지하며, 초기화 기능은 제공하지 않습니다. 병합된 일정 전체를 검증합니다.

기존 stage/schedule 변경은 비관적 쓰기 잠금을 사용합니다.
조회 및 수정 전에 ExpeditionTeamAccess가 팀 권한을 검증하고 실제 사용자 ID를 반환해야 합니다.
미인증 요청은 401, 팀 권한 연동 빈이 없거나 권한이 없으면 403입니다.
TeamAccessService가 실제 TeamMember 소속을 검증합니다. 팀원은 조회·단계·일정·공지·체크리스트 변경이 가능하며, 다른 팀은 403, 없는 팀은 404입니다.
Swagger JWT 전환 과정에서 LocalSecurityConfig를 제거했습니다. 로그인·초대 로직은 유지하며, 원정 API는 실제 JWT 인증과 ExpeditionTeamAccess 권한 연동이 필요합니다.

Expedition은 공고 참여 제안 전송 트랜잭션에서 생성됩니다. 기본 체크리스트 자동 생성은 아직 구현되어 있지 않습니다.
팀별 원정 목록과 합류 인원은 GET /api/v1/teams/{teamId}/expeditions로 조회합니다. 제안 응답은 PATCH /api/v1/proposal-recipients/{proposalRecipientId}/response로 처리합니다.
