# 받은 제안 API (B03 / C05)

팀원 경로로 통일했습니다. 이전 /api/v1/proposals/received 및 그 하위 pending-count 별칭은 제거했습니다.

모든 요청은 기존 JWT 인증과 동일하게 `Authorization: Bearer <accessToken>`이 필요합니다.
사용자 ID는 요청으로 받지 않고 인증된 사용자 ID를 사용합니다.

## 받은 제안 목록

`GET /api/v1/proposal-recipients/received`

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

`GET /api/v1/proposal-recipients/pending-count`

```json
{"pendingCount": 2}
```

읽음 여부와 무관하게 본인 수신 기록의 PENDING 개수를 반환합니다. 없으면 0입니다.
이 값으로 알림 점을 표시하지 않습니다. 알림 점은 [새 소식 알림 API](proposal-news-api.md)의 hasUnread를 사용합니다.

## 받은 제안 읽음 처리

`PATCH /api/v1/proposal-recipients/{proposalRecipientId}/read`

요청 본문은 없고 받은 제안 한 건을 반환합니다. 본인의 제안만 가능하며 최초 readAt을 저장하고 재요청 시 유지합니다.
화면에 성공적으로 표시한 제안에 호출합니다. 읽음 처리는 PENDING 상태와 미응답 개수를 바꾸지 않습니다.

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
  "respondedAt": "2026-11-09T18:30:00",
  "responseVersion": 1
}
```

- 본인이 수신자인 제안에 응답할 수 있으며 JOINED ↔ DECLINED 변경을 허용합니다. PENDING으로 되돌릴 수 없습니다.
- 실제 상태가 바뀔 때만 status·respondedAt(한국 시각)·responseVersion을 갱신합니다. 같은 응답 재전송은 200이며 시간·버전·새 응답 알림을 바꾸지 않습니다.
- 응답은 받은 제안을 확인한 것으로 처리하여 readAt이 없으면 함께 저장합니다.
- JOINED로 바뀌면 해당 원정의 팀에 아직 가입하지 않은 사용자만 MEMBER로 등록합니다. 새 가입은 기존 초대 정책과 동일하게 온보딩 완료가 필요합니다.
- 기존 팀원은 중복 등록하지 않고 LEADER/MEMBER 역할을 유지합니다.
- DECLINED는 기존 팀 소속을 삭제하지 않습니다.
- 응답 상태와 새 팀원 등록은 같은 트랜잭션입니다. 가입 실패 시 응답도 저장하지 않습니다.
- 수신 기록 쓰기 잠금으로 동시 응답을 직렬 처리합니다. 새 가입 시 대상 팀에도 쓰기 잠금을 사용합니다.
- 성공은 200, 미인증은 401, 다른 사람의 수신 기록 또는 새 가입자의 온보딩 미완료는 403, 없는 수신 기록·가입 대상 팀은 404입니다.
- DECLINED로 바뀌면 원정 합류 인원에서 제외되고, 다시 JOINED가 되면 포함됩니다. 팀 탈퇴·역할 변경·원정 전체 진행 단계 변경은 하지 않습니다.
- 잘못된 ID, status 누락·null·PENDING·알 수 없는 값은 400입니다.

AuthController와 JWT·CSRF 설정은 변경하지 않았습니다.
