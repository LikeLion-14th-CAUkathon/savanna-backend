# D01 오아시스 원정 목록

GET /api/v1/teams/{teamId}/expeditions

```json
[
  {
    "expeditionId": 1,
    "announcementId": 10,
    "title": "헬스케어 AI 아이디어 공모전",
    "deadline": "2026-11-14T18:00:00",
    "stage": "DRAFT",
    "joinedCount": 2
  }
]
```

- 해당 팀의 원정만 조회합니다. 원정이 없으면 []입니다.
- 공고 마감 오름차순, 같은 마감은 expeditionId 오름차순입니다.
- 팀의 기존 원정 지도이므로 마감·제출 완료·비공개로 전환된 공고도 포함합니다.
- deadline은 기존 공고와 동일한 한국 현지 시각입니다.
- joinedCount는 해당 원정에 속한 모든 제안 수신자 중 JOINED 상태의 고유 사용자 수입니다.
- 같은 사람이 여러 제안을 수락해도 1명입니다. PENDING·DECLINED는 제외합니다.
- 제안 발신자는 수신자로서 JOINED한 기록이 없다면 포함하지 않습니다.
- 같은 원정에 JOINED 기록과 다른 상태 기록이 함께 있으면 JOINED 기록 기준으로 포함합니다.
- 두 번의 일괄 조회(공고 포함 원정 목록, 팀 전체 원정별 집계)로 반환하며 원정마다 추가 조회하지 않습니다.

인증 Principal 및 ExpeditionTeamAccess의 팀 조회 권한 확인이 필요합니다.
미인증은 401, 권한이 없거나 실제 팀 권한 구현이 없으면 403입니다.
팀 ID가 0 이하이거나 타입이 맞지 않으면 400입니다.
팀 자체 존재 여부 및 소속 검증은 Team 담당 ExpeditionTeamAccess 구현에서 처리해야 합니다.
TeamAccessService가 실제 TeamMember 소속을 검증합니다. 다른 팀은 403, 없는 팀은 404입니다.
테스트에서는 테스트 전용 권한 구현을 사용합니다.

빈 Proposal/ProposalRecipient에 집계용 JPA 기본 모델을 추가했습니다.
Proposal은 Expedition, 발신 User, 제안 메시지와 생성 시각을 저장합니다.
ProposalRecipient는 Proposal, 수신 User, PENDING/JOINED/DECLINED, 읽음·응답 시각을 저장합니다.
동일 제안·사용자는 중복 생성할 수 없습니다.
제안 전송은 POST /api/v1/announcements/{announcementId}/proposals, 응답은 PATCH /api/v1/proposal-recipients/{proposalRecipientId}/response로 구현되어 있습니다.
로그인·초대·JWT·SecurityConfig 및 비밀 설정은 변경하지 않았습니다.
