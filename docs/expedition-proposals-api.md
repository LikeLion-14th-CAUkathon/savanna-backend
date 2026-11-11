# 원정의 제안과 응답 현황 (D02 지원 조건 탭)

`GET /api/v1/expeditions/{expeditionId}/proposals`

기존 JWT 인증과 동일하게 `Authorization: Bearer <accessToken>`이 필요합니다.

```json
[
  {
    "proposalId": 3,
    "senderId": 4,
    "senderName": "김사바",
    "message": "함께 참가해요!",
    "createdAt": "2026-11-09T18:00:00",
    "totalCount": 2,
    "joinedCount": 1,
    "declinedCount": 0,
    "pendingCount": 1,
    "recipients": [
      {
        "proposalRecipientId": 12,
        "userId": 5,
        "name": "이초원",
        "status": "JOINED",
        "readAt": null,
        "respondedAt": "2026-11-09T18:30:00"
      },
      {
        "proposalRecipientId": 13,
        "userId": 6,
        "name": "박오아",
        "status": "PENDING",
        "readAt": null,
        "respondedAt": null
      }
    ]
  }
]
```

- 원정이 속한 팀의 실제 TeamMember만 조회할 수 있습니다. 발신자가 아닌 팀원도 가능합니다.
- 팀원이 아닌 수신자는 이 API에 접근할 수 없습니다. 받은 제안 API를 사용해야 합니다.
- 해당 원정의 제안과 수신 기록만 반환합니다. 다른 원정의 응답이나 현재 팀원 전체를 합쳐서 집계하지 않습니다.
- 합류 JOINED, 쉬어가기 DECLINED, 미응답 PENDING의 개수와 수신자별 상태를 같은 조회 결과에서 구성합니다.
- 수신자로 등록되지 않은 발신자는 집계에 포함하지 않습니다.
- 제안은 생성 시각 내림차순, 같은 시각이면 proposalId 내림차순입니다. 수신자는 수신 기록 ID 오름차순입니다.
- 현재 모델은 원정당 제안을 최대 하나 허용하며, 응답 형식은 목록입니다. 제안이 없으면 `[]`입니다.
- 수신자가 없으면 recipients는 `[]`, 모든 집계는 0입니다.
- 마감·비공개 공고 및 제출 완료 원정도 기존 기록을 조회할 수 있습니다.
- 조회로 읽음·응답 상태를 변경하지 않습니다. 페이지 처리는 없습니다.
- 수신자별 responseVersion과 responseUnread도 반환합니다. 발신자가 새 응답을 확인하려면 [응답 확인 API](proposal-news-api.md)를 별도로 호출합니다. 다른 팀원의 조회로 발신자의 알림을 지우지 않습니다.
- 제안 목록과 수신자 목록을 각각 일괄 조회하며, 항목마다 추가 조회하지 않습니다.
- 성공 200, 미인증 401, 다른 팀 403, 없는 원정·팀 404, 잘못된 ID 400입니다.

발신자 전용 `GET /api/v1/proposals/{proposalId}/recipients`의 권한 정책은 그대로 유지합니다.
AuthController와 JWT·CSRF 설정은 변경하지 않았습니다.
