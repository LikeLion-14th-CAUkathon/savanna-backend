# 제출 체크리스트 API

| 요청 | 응답 |
| --- | --- |
| GET /api/v1/expeditions/{expeditionId}/checklists | 200: items, completedCount, totalCount, progress |
| POST /api/v1/expeditions/{expeditionId}/checklists | 201: 생성한 항목, Location 헤더 |
| PATCH /api/v1/expedition-checklists/{checklistId} | 200: 수정한 항목 |
| DELETE /api/v1/expedition-checklists/{checklistId} | 204 |

추가: {"title":"기획서 PDF","sortOrder":0}
title은 공백 불가, 최대 1000자입니다. sortOrder는 0 이상이며 생략하면 기존 최대값+1, 최초는 0입니다.
추가 항목의 isChecked는 false입니다. 팀이 추가하는 수동 항목용 API입니다.

수정: {"isChecked":true} 또는 {"title":"최종 기획서","sortOrder":2}
체크 해제는 {"isChecked":false}입니다. 서버의 현재 값을 뒤집는 방식이 아니라 원하는 상태를 명시합니다.
생략/null 필드는 유지하고 빈 객체는 변경 없는 요청으로 처리합니다.
sortOrder 수정은 해당 항목의 값만 바꾸며 다른 항목을 밀거나 당기지 않습니다.
동일 sortOrder는 checklistId 오름차순으로 반환합니다. 삭제 후에도 나머지 순서를 재번호화하지 않습니다.

목록 응답 예:
{"items":[{"checklistId":1,"expeditionId":1,"title":"기획서 PDF","isChecked":true,"sortOrder":0}],"completedCount":1,"totalCount":1,"progress":"1/1"}
항목이 없으면 items:[], completedCount:0, totalCount:0, progress:"0/0"입니다.
조회 결과 한 번으로 목록과 진행도를 계산하므로 서로 다른 조회 결과를 섞지 않습니다.

원정·항목이 없으면 404, 잘못된 ID/항목/순서는 400입니다.
미인증은 401, 팀 권한이 없거나 ExpeditionTeamAccess 실제 구현이 없으면 403입니다.
테스트의 팀 권한 구현은 테스트에서만 등록하며, 로그인·초대·보안 우회 구현은 추가하지 않았습니다.

공고 제출물 기반 자동 생성은 추후 C04의 Expedition 생성 트랜잭션에서 연결해야 합니다.
현재 작업본에 원정 엔티티·Repository와 팀 권한 인터페이스가 없어 기존 설계대로 복구했습니다.
기존 공지 테스트의 누락된 팀 설정 참조를 공용 테스트 설정으로 교체했습니다.
체크리스트 외 원정 API, 로그인·보안 설정, application.yml, .gitignore는 변경하지 않았습니다.
