# 로컬 Swagger 및 참가 조건 확인

## Swagger 테스트

인증 우회용 LocalSecurityConfig는 제거했습니다. 실제 JWT 테스트 방법은 [Swagger JWT 테스트](swagger-jwt-testing.md)를 참고하세요.

## 참가 조건 API

`GET /api/v1/announcements/{announcementId}/eligibility?teamId=1`

teamId는 선택입니다. 생략 시 가입한 팀이 정확히 하나일 때 자동 선택합니다. 팀이 없거나 여러 개면 TEAM_INFO_REQUIRED를 반환하므로 teamId를 지정해야 합니다.
결과는 저장하지 않습니다. 공개 공고만 조회하며 비공개·없는 공고는 404, 잘못된 ID는 400입니다.

- SATISFIED: 자동 비교한 모든 조건 충족
- MISMATCH: 확인된 불일치가 하나 이상 존재
- TEAM_INFO_REQUIRED: 팀·팀원 또는 공고 조건 확인 필요

조건별 code/status/message를 반환합니다. 불일치와 확인 필요가 함께 있으면 전체 상태는 MISMATCH입니다.
인원 범위는 확인된 최소·최대 조건을 비교합니다. 양쪽 모두 null이면 미확인으로 처리합니다.
재학 조건은 전체 문자열이 재학생, 휴학생, 졸업생, 재학생 및 휴학생, 재학생 또는 휴학생, 제한 없음, 누구나 등 명확한 값인 경우만 비교합니다.
복잡한 참가 대상 문구는 자동으로 충족 처리하지 않습니다. region은 현장 참석 지역이므로 거주 자격으로 해석하지 않습니다.

## 현재 팀 연동의 제한

TeamAccessService가 EligibilityTeamReader를 구현하여 실제 TeamMember와 User의 재학 상태를 조회합니다.
JWT 인증의 Long 사용자 ID와 DB 사용자 존재 여부를 확인합니다. 지정한 팀이 없으면 404, 다른 팀이면 403입니다.
재학 상태가 없는 팀원도 인원 집계에 포함하고, 재학 조건은 확인 필요로 반환합니다.
