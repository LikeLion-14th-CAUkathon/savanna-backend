# 로컬 Swagger 및 참가 조건 확인

## Swagger 테스트

인증 우회용 LocalSecurityConfig는 제거했습니다. 실제 JWT 테스트 방법은 [Swagger JWT 테스트](swagger-jwt-testing.md)를 참고하세요.

## 참가 조건 API

`GET /api/v1/announcements/{announcementId}/eligibility?teamId=1`

teamId는 선택입니다. 생략 시 팀 조회 연동 구현이 인증 사용자의 현재 팀을 선택합니다.
결과는 저장하지 않습니다. 공개 공고만 조회하며 비공개·없는 공고는 404, 잘못된 ID는 400입니다.

- SATISFIED: 자동 비교한 모든 조건 충족
- MISMATCH: 확인된 불일치가 하나 이상 존재
- TEAM_INFO_REQUIRED: 팀·팀원 또는 공고 조건 확인 필요

조건별 code/status/message를 반환합니다. 불일치와 확인 필요가 함께 있으면 전체 상태는 MISMATCH입니다.
인원 범위는 확인된 최소·최대 조건을 비교합니다. 양쪽 모두 null이면 미확인으로 처리합니다.
재학 조건은 전체 문자열이 재학생, 휴학생, 졸업생, 재학생 및 휴학생, 재학생 또는 휴학생, 제한 없음, 누구나 등 명확한 값인 경우만 비교합니다.
복잡한 참가 대상 문구는 자동으로 충족 처리하지 않습니다. region은 현장 참석 지역이므로 거주 자격으로 해석하지 않습니다.

## 현재 팀 연동의 제한

Team·TeamMember 모델 및 조회 코드가 없어 EligibilityTeamReader 인터페이스를 두었습니다.
팀 담당 구현은 인증 Principal과 teamId를 받아 접근 권한을 검증하고 실제 가입된 팀원 전체의 재학 상태를 반환해야 합니다.
해당 Spring 빈이 없거나 인증 사용자가 없으면 TEAM_INFO_REQUIRED를 반환합니다.
따라서 현재 실행 환경에서는 실제 팀으로 SATISFIED/MISMATCH를 확인하려면 이 연동 구현이 필요합니다.
로컬 익명 요청에 임의의 사용자나 팀 정보를 만들어 넣지 않습니다.
