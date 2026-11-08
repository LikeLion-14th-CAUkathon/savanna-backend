# Expedition API

- GET /api/v1/expeditions/{expeditionId}: 공고 요약, 단계, 목표일, 제출 완료 여부
- PATCH /api/v1/expeditions/{expeditionId}/stage: {"stage":"DRAFT"} 등. 단계 변경과 이력을 같은 트랜잭션으로 저장합니다. 동일 단계 요청은 이력을 만들지 않습니다.
- GET /api/v1/expeditions/{expeditionId}/stage-histories?page=0&size=10: 최신순 이력. size는 1~100.
- PATCH /api/v1/expeditions/{expeditionId}/schedule: kickoffDate/draftDate/reviewDate/submitTargetDate를 YYYY-MM-DD로 전달합니다. 생략 또는 null은 기존 값을 유지하며, 초기화 기능은 제공하지 않습니다. 병합된 일정 전체를 검증합니다.

기존 stage/schedule 변경은 비관적 쓰기 잠금을 사용합니다.
조회 및 수정 전에 ExpeditionTeamAccess가 팀 권한을 검증하고 실제 사용자 ID를 반환해야 합니다.
미인증 요청은 401, 팀 권한 연동 빈이 없거나 권한이 없으면 403입니다.
현재 Team/TeamMember 구현이 없어 실제 사용자 권한 연동은 남아 있습니다. 테스트 전용 구현만 테스트에 등록했습니다.
로그인·초대·LocalSecurityConfig는 변경하지 않았습니다. 현재 local 보안 설정도 원정 API 요청은 차단합니다.

Expedition 생성은 추후 C04 제안 전송 트랜잭션에서 체크리스트 생성과 함께 처리합니다.
팀별 원정 목록 및 합류 인원, 제안·응답 현황은 제안/수신자 구현과 함께 연결할 예정이며 이번 범위에 포함되지 않습니다.
