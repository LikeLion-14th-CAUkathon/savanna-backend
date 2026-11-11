# 팀 새 소식: 읽음·응답 변경·알림 점 (B03)

기존 JWT 인증을 사용합니다. 이번 경로 통합에서 기존 GET /api/v1/proposals/received와 GET /api/v1/proposals/received/pending-count는 제거했습니다.

| 역할 | 메서드·경로 |
|---|---|
| 내가 받은 제안 | GET /api/v1/proposal-recipients/received |
| 내가 받은 미응답 개수 | GET /api/v1/proposal-recipients/pending-count |
| 받은 제안 읽음 | PATCH /api/v1/proposal-recipients/{proposalRecipientId}/read |
| 받은 제안 응답·변경 | PATCH /api/v1/proposal-recipients/{proposalRecipientId}/response |
| 내가 보낸 제안 | GET /api/v1/proposals/sent |
| 보낸 제안의 응답 현황 | GET /api/v1/proposals/{proposalId}/recipients |
| 실제로 본 응답 확인 | PATCH /api/v1/proposals/{proposalId}/recipients/read |
| 알림 점 상태 | GET /api/v1/proposals/notifications |

## 알림 점

```json
{"hasUnread":true,"unreadProposalCount":1,"unreadResponseCount":2}
```

- 프런트엔드는 hasUnread로 B02 봉투 버튼과 하단 팀 공간의 점 표시 여부를 결정합니다. 숫자 배지는 표시하지 않습니다.
- unreadProposalCount: 본인이 받은 제안 중 readAt이 없는 수신 기록 수입니다.
- unreadResponseCount: 본인이 보낸 제안 중 아직 확인하지 않은 최신 응답이 있는 수신 기록 수입니다. 한 수신자가 여러 번 바꿔도 미확인 최신 응답 한 건으로 셉니다.
- 두 값 중 하나라도 양수이면 hasUnread=true입니다. 미응답 pendingCount로 대신하지 않습니다.
- 읽었지만 아직 응답하지 않은 제안은 pendingCount에는 남고 새 제안 알림에서는 제외됩니다.
- 응답 현황을 읽어도 새 제안이 남아 있으면 점이 유지됩니다. 반대의 경우도 동일합니다.
- 조회·확인 실패 시 프런트엔드는 기존 알림을 유지하고 다시 조회합니다.

## 받은 제안 확인

GET received는 상태를 바꾸지 않습니다. 성공적으로 화면에 표시한 수신 기록마다 PATCH /{id}/read를 호출합니다.
이 API는 본인만 가능하며 최초 읽음 시간을 한국 시각으로 저장합니다. 반복 호출은 최초 시각을 유지하고 응답 상태·respondedAt을 바꾸지 않습니다.
응답을 직접 전송하는 것도 확인한 것으로 처리합니다.

## 응답 변경과 원정 참여

응답 요청은 기존처럼 {"status":"JOINED"} 또는 {"status":"DECLINED"}입니다.

- 최초 응답과 JOINED ↔ DECLINED 변경을 허용합니다. PENDING으로 되돌리는 요청은 400입니다.
- 상태가 바뀔 때만 respondedAt과 responseVersion을 갱신합니다. 같은 상태 재전송은 200이며 시각·버전은 유지합니다.
- 원정 참여는 현재 수신 기록의 JOINED로 판단합니다. 응답 집계·D01 합류 인원·D02 응답 상태가 현재 값에 따라 바뀝니다.
- 제안자는 합류/응답 집계에서 제외합니다.
- JOINED로 바뀔 때 아직 팀원이 아니면 기존 온보딩 확인 후 MEMBER로 가입시킵니다. 같은 상태 재시도만으로 탈퇴한 팀에 재가입시키지 않습니다.
- DECLINED는 원정 참여 변경이며 팀 탈퇴가 아닙니다. 기존 TeamMember와 LEADER/MEMBER 역할, 다른 원정의 참여는 유지합니다.
- 응답·읽음·버전·필요한 팀 가입은 하나의 트랜잭션에서 수신 기록 쓰기 잠금 아래 처리합니다.

## 발신자의 새 응답 확인

GET recipients의 각 수신자에 아래 필드가 추가됩니다. D02 원정 제안 조회에도 동일하게 포함됩니다.

```json
{
  "proposalRecipientId": 12,
  "userId": 5,
  "name": "팀원",
  "status": "JOINED",
  "readAt": "2026-11-11T10:00:00",
  "respondedAt": "2026-11-11T10:05:00",
  "responseVersion": 3,
  "responseUnread": true
}
```

발신자가 화면에 성공적으로 표시한 응답의 ID와 버전만 확인 처리합니다.

`PATCH /api/v1/proposals/{proposalId}/recipients/read`

```json
{"responses":[{"proposalRecipientId":12,"responseVersion":3}]}
```

```json
{"proposalId":3,"acknowledgedCount":1}
```

- 발신자 본인만 해당 제안에 속한 수신 기록을 확인할 수 있습니다.
- PENDING은 responseVersion=0으로 응답이 없으므로 확인 요청에서 제외합니다.
- 한 요청에 1~100개의 서로 다른 수신 기록을 전달합니다.
- 본 버전이 1인데 그 사이 2가 도착했다면 1까지만 확인하며 2는 미확인으로 유지합니다. 시각 비교 대신 버전을 사용해 동일 시각의 응답 변경도 놓치지 않습니다.
- 이미 확인한 버전의 재요청은 200, acknowledgedCount=0입니다.
- 현재보다 큰 버전은 409입니다. 잘못된 본문·다른 제안의 수신 기록·중복 ID는 400, 타인의 제안은 403, 없는 제안/수신 기록은 404입니다.
- 한 요청의 모든 항목을 잠그고 검증한 뒤 적용합니다. 중간 실패 시 일부만 확인 처리하지 않습니다.
- GET sent, GET recipients, GET expedition proposals는 자동 확인하지 않습니다. 다른 사람의 조회로 발신자의 새 응답 알림이 지워지지 않습니다.
- 화면은 확인 API 성공 후 알림 점 API를 다시 조회해야 합니다. 실제 프런트엔드 연결은 별도 작업입니다.

## 기존 DB

response_version, response_read_version은 기본값 0, response_read_at은 nullable로 추가합니다.
기존 읽음·응답 시각과 TeamMember는 초기화하지 않습니다. 기존 응답은 버전 1의 미확인 응답으로 취급해 최초 확인 후 점이 사라집니다.
H2용 명시적 변경 SQL은 [응답 확인 컬럼 추가](sql/proposal-response-news-h2.sql)에 있습니다. 실제 앱 DB에는 이번 작업에서 적용하지 않았습니다.
