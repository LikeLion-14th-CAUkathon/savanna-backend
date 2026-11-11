# 보낸 제안 API (C05·C05-02)

모든 요청은 `Authorization: Bearer <accessToken>`이 필요합니다. 사용자 ID는 기존 JWT 인증에서 가져옵니다.

## 내가 보낸 제안 목록

`GET /api/v1/proposals/sent`

```json
[
  {
    "proposalId": 3,
    "expeditionId": 2,
    "teamId": 1,
    "announcementId": 10,
    "announcementTitle": "아이디어 공모전",
    "message": "함께 참가해요!",
    "createdAt": "2026-11-09T18:00:00"
  }
]
```

- 본인이 발신자인 제안만 반환합니다. 팀원이 보낸 제안은 포함하지 않습니다.
- 생성 시각 내림차순, 같은 시각이면 proposalId 내림차순입니다.
- 목록이 없으면 `[]`입니다. 페이지 처리는 없습니다.
- 마감·비공개 공고도 기존에 보낸 제안의 이력으로 포함합니다.
- 원정과 공고를 함께 조회하여 항목마다 추가 조회하지 않습니다.

## 보낸 제안 응답 현황

`GET /api/v1/proposals/{proposalId}/recipients`

```json
{
  "proposalId": 3,
  "totalCount": 3,
  "joinedCount": 1,
  "declinedCount": 1,
  "pendingCount": 1,
  "recipients": [
    {
      "proposalRecipientId": 12,
      "userId": 5,
      "name": "김사바",
      "status": "JOINED",
      "readAt": null,
      "respondedAt": "2026-11-09T18:30:00"
    },
    {
      "proposalRecipientId": 13,
      "userId": 6,
      "name": "이초원",
      "status": "DECLINED",
      "readAt": null,
      "respondedAt": "2026-11-09T18:35:00"
    },
    {
      "proposalRecipientId": 14,
      "userId": 7,
      "name": "박오아",
      "status": "PENDING",
      "readAt": null,
      "respondedAt": null
    }
  ]
}
```

- 발신자 본인만 조회 가능합니다. 같은 팀원이나 제안 수신자라도 다른 발신자의 응답 현황은 403입니다.
- 해당 제안의 수신 기록만 집계합니다. 현재 팀원 목록이나 다른 제안의 응답은 포함하지 않습니다.
- JOINED는 합류, DECLINED는 쉬어가기, PENDING은 미응답입니다.
- 집계와 개인별 상태는 같은 조회 결과를 사용합니다. 수신 기록 ID 오름차순으로 반환합니다.
- 수신자가 없으면 모든 집계는 0이고 recipients는 `[]`입니다.
- 조회는 readAt이나 respondedAt, 응답 상태를 변경하지 않습니다.
- 수신자별 responseVersion과 responseUnread도 반환합니다. 응답을 화면에 정상 표시한 뒤 관찰한 버전으로 [새 응답 확인 API](proposal-news-api.md)를 호출합니다.
- 제안자 본인은 응답 인원과 수신자 목록에서 제외합니다.
- 성공 200, 미인증 401, 다른 발신자의 제안 403, 없는 제안 404, 잘못된 ID 400입니다.

AuthController, JWT·CSRF 설정은 변경하지 않았습니다.
