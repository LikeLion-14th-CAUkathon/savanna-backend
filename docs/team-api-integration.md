# 팀 API 연동

TeamAccessService가 EligibilityTeamReader, ExpeditionTeamAccess, ProposalTeamAccess를 구현합니다.
JWT 필터가 생성한 인증 객체의 Long principal과 실제 사용자 존재 여부를 확인하고 TeamMember로 소속을 검증합니다.
팀원은 원정의 조회와 변경이 가능합니다. 팀 정보 자체의 수정은 기존 TeamService의 팀장 제한을 유지합니다.

## 제안 전송

POST /api/v1/announcements/{announcementId}/proposals

```json
{"message":"같이 참가해요!", "teamId":1}
```

- teamId는 선택이며 지정한 팀의 소속을 확인합니다.
- 생략 시 가입한 팀이 하나이면 자동 선택합니다. 팀이 없거나 여러 개면 400입니다.
- 실제 가입한 팀원 중 발신자를 제외한 사용자에게 PENDING 수신 기록을 생성합니다.
- 없는 팀은 404, 다른 팀은 403입니다.

## 참가 조건 확인

teamId 생략 시 단일 가입 팀만 자동 선택합니다. 팀이 없거나 여러 개면 TEAM_INFO_REQUIRED입니다.
재학 상태가 없는 팀원도 인원수에는 포함하고 재학 조건을 확인 필요로 처리합니다.

## 남은 기능

받은 제안 목록·미응답 개수·합류/쉬어가기 응답 API는 [받은 제안 API](received-proposals-api.md)에 정리되어 있습니다.
보낸 제안 목록·응답 현황은 [보낸 제안 API](sent-proposals-api.md)에 정리되어 있습니다.
원정별 제안·응답 현황은 [원정 제안 API](expedition-proposals-api.md)에 정리되어 있습니다.
기본 제출 체크리스트 자동 생성은 아직 구현되지 않았습니다.
