# 원정 공지 API

공지의 API 경로는 기존 설계대로 tasks를 사용합니다. 종류(kind) 구분 없이 제목/내용(title), 날짜(dueDate), 선택 시간(dueTime), 완료 여부(isDone)를 관리합니다.

| 요청 | 동작 |
| --- | --- |
| GET /api/v1/expeditions/{expeditionId}/tasks | 날짜 오름차순 목록, 없으면 [] |
| POST /api/v1/expeditions/{expeditionId}/tasks | 공지 생성, 201 및 생성 응답 |
| PATCH /api/v1/expedition-tasks/{taskId} | 부분 수정, 200 및 수정 응답 |
| DELETE /api/v1/expedition-tasks/{taskId} | 삭제, 204 |

추가 요청 예시:

```json
{"title":"제출 양식 같이 채우기","dueDate":"2026-11-14","dueTime":"21:00"}
```

title은 필수이며 공백을 제외한 내용이 있어야 하고 최대 1000자입니다. dueDate는 필수, dueTime은 생략 또는 null이 가능합니다.
완료 여부는 추가 시 false입니다.
시간은 원정 일정과 동일하게 한국 현지 날짜/시간으로 취급합니다. 서버가 임의로 시간대를 변환하지 않습니다.

수정 예시:

```json
{"title":"제출 양식 최종 확인","isDone":true}
```

생략한 필드는 기존 값을 유지합니다.
dueTime을 명시적으로 null로 보내면 시간을 삭제합니다. dueTime 생략은 기존 시간 유지입니다.
title/dueDate/isDone의 null은 기존 값을 유지합니다. isDone:false로 완료를 취소할 수 있습니다.
빈 객체는 변경 없는 요청으로 처리합니다. 원정 소속 변경은 지원하지 않습니다.

정렬: dueDate 오름차순 → 같은 날 시간 있는 공지 먼저 → dueTime 오름차순 → taskId 오름차순.
404는 없는 원정·공지, 400은 잘못된 ID/내용/날짜/시간을 의미합니다.
로그인과 해당 원정의 팀 권한이 필요합니다. ExpeditionTeamAccess 실제 구현이 없으면 403으로 차단됩니다.
테스트에서만 팀 권한 구현을 등록했으며, 실제 권한을 우회하는 설정을 추가하지 않았습니다.
application.yml 및 로그인·초대·보안 설정은 변경하지 않았습니다.
