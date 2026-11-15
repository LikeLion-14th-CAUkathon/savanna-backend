# 한 계정 한 팀 정책과 Swagger 응답

## API 동작

| API | 정책 |
|---|---|
| POST /api/v1/teams | 이미 팀 소속이면 409. 팀/팀원 레코드를 추가하지 않음 |
| POST /api/v1/invitations/{token}/accept | 다른 팀 또는 같은 팀 소속이면 409. 미소속 계정만 가입 |
| PATCH /api/v1/proposal-recipients/{proposalRecipientId}/response | 다른 팀 소속 계정의 JOINED 요청은 409. 기존 수신 상태·응답 시각·읽음 시각·알림 버전 유지 |

오류 응답:

```json
{"code":"ALREADY_IN_TEAM","message":"이미 소속된 팀이 있습니다. 한 계정은 한 팀에만 소속될 수 있습니다.","errors":[]}
```

제안 대상과 같은 팀에 속한 계정은 JOINED/DECLINED 응답을 변경할 수 있습니다. DECLINED는 원정 참여 응답이며 팀 탈퇴가 아닙니다. 팀장 역할과 팀 소속은 유지합니다. 이미 가입한 팀의 초대 링크 재수락은 기존처럼 409입니다.

팀 생성·초대 가입·제안 응답이 동일한 users 행의 비관적 쓰기 잠금을 먼저 획득한 뒤 팀 소속을 검사합니다. 서로 다른 팀에 대한 동시 가입 요청도 사용자 단위로 직렬화합니다. team_member.user_id 유니크 제약을 함께 추가합니다.

## Swagger

- 초대 생성/미리보기/가입/목록의 `ResponseEntity<?>`를 `DataResponse<실제 DTO>`로 변경하여 응답 `data` 필드와 목록 항목을 표시합니다. JSON 구조는 유지합니다.
- 초대 폐기·로그아웃은 status/message 응답 DTO, CSRF는 csrfToken 응답 DTO를 명시합니다.
- 위 가입 경로의 409에는 ErrorResponse 스키마와 ALREADY_IN_TEAM 예시를 표시합니다.
- 기존 명시된 오류 응답이 성공 DTO를 상속하지 않도록 정리하고, API JSON 응답 문서의 wildcard 미디어 타입을 application/json으로 표시합니다.
- 인증 없이 접근 가능한 초대 미리보기 GET에는 Bearer 인증 표시를 제거합니다. 실제 보안 설정은 변경하지 않습니다.
- 팀 사진·캐릭터·초대 API와 오류 본문 및 전체 내부 스키마 참조를 `/v3/api-docs` 테스트로 검증합니다.

Swagger UI의 Responses > Example Value/Schema는 문서 스키마입니다. Try it out > Response body는 현재 실행 중인 서버의 실제 결과입니다. 새 코드 배포·재시작 및 문서 새로고침이 필요합니다. 캐릭터/동의서 같은 원본 데이터 미설정 값은 nullable이며 스키마 추가가 실제 값을 생성하지는 않습니다.

## 기존 데이터와 배포

기존 다중 소속을 임의로 해소하지 않습니다. EC2나 실제 로컬 앱 DB는 이 작업에서 변경하지 않습니다.

1. 앱을 정상 종료하고 운영 DB/JAR을 백업합니다.
2. `docs/sql/single-team-membership-h2.sql`의 첫 SELECT만 먼저 실행합니다.
3. 결과가 있으면 배포/제약 추가를 중단하고 사용자별 유지할 팀을 결정합니다. 원정·제안·역할 등 연관 데이터 검토 없이 team_member를 임의 삭제하지 않습니다.
4. 충돌이 없으면 SQL의 유니크 제약을 추가합니다. 다른 경로로 INSERT해도 한 사용자의 두 번째 팀 소속은 거부됩니다.
5. 새 JAR 배포 후 재시작하고 실제 생성·초대·제안 응답 및 `/v3/api-docs`를 확인합니다.

기존 다중 소속 데이터가 남아 있는 DB에 새 JAR의 자동 DDL 적용만 맡겨 배포하지 마세요. 먼저 감사·정리·명시적 제약 추가를 완료해야 합니다. 이 제약은 테스트에서는 메모리 H2에만 적용합니다.

검증 결과: `test bootJar` 성공, 전체 182개 테스트 통과(실패/오류 0). 신규 검증에는 생성·초대·제안 수락 차단, 거절된 제안의 상태/시각/버전 보존, 같은 팀 응답 변경과 역할 유지, 동시 팀 생성 시 단일 가입, DB 유니크 제약 및 충돌 데이터 보존, Swagger 응답 필드·상태 코드·내부 스키마 참조 검증을 포함합니다.
