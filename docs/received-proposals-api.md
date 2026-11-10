# 받은 제안 API (C05)

모든 요청은 기존 JWT 인증과 동일하게 `Authorization: Bearer <accessToken>`이 필요합니다.
사용자 ID는 요청으로 받지 않고 인증된 사용자 ID를 사용합니다.

## 받은 제안 목록

`GET /api/v1/proposals/received`

```json
[
  {
    "proposalRecipientId": 12,
    "proposalId": 3,
    "expeditionId": 2,
    "announcementId": 10,
    "announcementTitle": "아이디어 공모전",
    "senderName": "김사바",
    "message": "함께 참가해요!",
    "status": "PENDING",
    "createdAt": "2026-11-09T18:00:00",
    "readAt": null
  }
]
```

본인이 수신자인 제안을 모든 상태에 걸쳐 조회합니다. 제안 생성 시각 내림차순, 같은 시각이면 수신 기록 ID 내림차순입니다.
목록이 없으면 `[]`이며 페이지 처리는 없습니다. 조회는 readAt이나 응답 상태를 변경하지 않습니다.

## 미응답 개수

`GET /api/v1/proposals/received/pending-count`

```json
{"pendingCount": 2}
```

읽음 여부와 무관하게 본인 수신 기록의 PENDING 개수를 반환합니다. 없으면 0입니다.

## 제안 응답

`PATCH /api/v1/proposal-recipients/{proposalRecipientId}/response`

```json
{"status": "JOINED"}
```

합류는 `JOINED`, 쉬어가기는 `DECLINED`입니다. 대소문자를 구분합니다.

```json
{
  "proposalRecipientId": 12,
  "proposalId": 3,
  "expeditionId": 2,
  "teamId": 1,
  "status": "JOINED",
  "respondedAt": "2026-11-09T18:30:00"
}
```

- 본인이 수신자인 PENDING 제안만 응답할 수 있습니다.
- 응답 시 status와 respondedAt(한국 시각)을 갱신합니다.
- JOINED이면 해당 원정의 팀에 아직 가입하지 않은 사용자만 MEMBER로 등록합니다. 새 가입은 기존 초대 정책과 동일하게 온보딩 완료가 필요합니다.
- 기존 팀원은 중복 등록하지 않고 LEADER/MEMBER 역할을 유지합니다.
- DECLINED는 기존 팀 소속을 삭제하지 않습니다.
- 응답 상태와 새 팀원 등록은 같은 트랜잭션입니다. 가입 실패 시 응답도 저장하지 않습니다.
- 수신 기록 쓰기 잠금으로 동시 응답을 직렬 처리합니다. 새 가입 시 대상 팀에도 쓰기 잠금을 사용합니다.
- 성공은 200, 미인증은 401, 다른 사람의 수신 기록 또는 새 가입자의 온보딩 미완료는 403, 없는 수신 기록·가입 대상 팀은 404입니다.
- 이미 응답한 제안은 같은 응답을 다시 보내도 409입니다. 응답 변경은 지원하지 않습니다.
- 잘못된 ID, status 누락·null·PENDING·알 수 없는 값은 400입니다.

AuthController와 JWT·CSRF 설정은 변경하지 않았습니다.
