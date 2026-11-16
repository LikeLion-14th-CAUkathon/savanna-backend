# 온라인 공고의 지역 조건

`GET /api/v1/announcements/{announcementId}/eligibility`와 추천 후보 판정에 동일하게 적용합니다.

- 참가 가능 지역(`participationRegion`), 개최 지역(`region`), 장소 안내(`venueNote`) 중 하나에 `온라인`이 포함되면 지역 조건을 충족한 것으로 처리합니다. 글자 사이 공백과 온라인·오프라인 병행 표시도 포함합니다.
- 응답의 `conditions`에 `code: PARTICIPATION_REGION`, `status: SATISFIED`가 내려갑니다.
- 온라인 접수 방법만으로는 온라인 공고로 판정하지 않습니다.
- 참가 인원·재학 상태·추가 자격 조건은 별도로 검사하므로 전체 `status`는 `MISMATCH` 또는 `TEAM_INFO_REQUIRED`일 수 있습니다.
- 온라인 표시가 없는 지역 제한 공고는 기존처럼 원문 확인이 필요합니다.

API 경로와 응답 필드 변경, DB 마이그레이션은 없습니다. 프런트는 전체 판정과 각 조건의 상태를 구분해서 표시해야 합니다.
