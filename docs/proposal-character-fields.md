# B03 제안 캐릭터 응답

| API | 추가 필드 |
|---|---|
| GET /api/v1/proposal-recipients/received | 각 항목의 senderId, senderCharacterId |
| PATCH /api/v1/proposal-recipients/{proposalRecipientId}/read | senderId, senderCharacterId |
| GET /api/v1/proposals/sent | 각 항목의 senderCharacterId |
| GET /api/v1/proposals/{proposalId}/recipients | recipients[].characterId |
| GET /api/v1/expeditions/{expeditionId}/proposals | 각 제안의 senderCharacterId, recipients[].characterId |

숫자 ID는 기존 프로필/팀 상세의 characterId(1~9)와 같습니다. 받은 제안의 senderCharacterId는 발신자, 응답 현황 recipients[].characterId는 해당 userId의 캐릭터입니다. 로그인 사용자나 팀장의 캐릭터를 복사하지 않습니다. 각 사용자의 현재 저장값을 반환하므로 프로필 변경 후 다시 조회하면 새 값이 표시됩니다. 기존 미설정 사용자는 null이며 임의 캐릭터를 배정하지 않습니다.

프런트는 팀 상세를 추가 조회하여 매칭할 필요가 없습니다. 이미지 컴포넌트가 문자열 캐릭터 코드를 사용한다면 기존 숫자 ID → 문자열 코드 매핑을 그대로 사용합니다. 기존 응답 구조와 필드는 유지하며 이번 필드만 추가합니다. DB 컬럼 추가나 데이터 마이그레이션은 필요하지 않습니다.

받은 제안 항목 예시(기존 필드는 생략):

```json
{"senderId":1,"senderName":"팀장","senderCharacterId":2}
```

응답 현황 항목 예시(기존 필드는 생략):

```json
{"userId":2,"name":"팀원","characterId":7,"status":"JOINED"}
```

EC2 및 실제 앱 DB에는 적용하지 않았습니다. 새 JAR 배포·재시작 후 Swagger와 실제 응답에 표시됩니다.

검증: 전체 185개 테스트 통과(실패/오류 0), bootJar 성공. 발신자/수신자별 HTTP 응답, 읽음 응답, 미설정 null, 프로필 변경 후 재조회, 원정 응답 및 Swagger 필드를 확인했습니다.
