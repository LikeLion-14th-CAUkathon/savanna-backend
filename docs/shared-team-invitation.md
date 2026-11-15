# 팀 공통 초대 링크

## 프런트 연결

`GET /api/v1/teams/{teamId}/invitations/current`를 Bearer 인증으로 호출합니다. 팀장과 일반 팀원 모두 같은 활성 영구 링크를 조회합니다. 브라우저별 localStorage나 `window.location.origin`으로 공유 링크를 만들지 말고 `data.inviteUrl`을 그대로 복사·공유합니다.

```json
{
  "status": 200,
  "message": "현재 초대 링크 조회 성공",
  "data": {
    "invitationId": 1,
    "teamId": 1,
    "token": "예시-토큰",
    "expiresAt": null,
    "inviteUrl": "https://savanna-frontend.vercel.app/invite/예시-토큰",
    "status": "ACTIVE"
  }
}
```

조회는 링크를 생성하거나 갱신하지 않습니다. 없으면 404이며 팀장이 기존 `POST /api/v1/teams/{teamId}/invitations`를 호출합니다. 기존 활성 영구 링크가 있으면 POST도 같은 링크를 재사용합니다. 일반 팀원의 생성·폐기 권한은 추가하지 않았습니다. 팀 소속이 아닌 계정은 조회 403, 미로그인은 401입니다.

기존 생성 응답에는 `inviteUrl`, `status`를 추가했습니다. 팀장용 목록 `GET /api/v1/teams/{teamId}/invitations`에도 각 항목의 `token`, `inviteUrl`을 추가했습니다. 활성 영구 링크만 토큰을 복원할 수 있습니다. 폐기·만료 링크와 과거 해시 전용 링크는 두 값이 null입니다. 목록 조회/폐기는 기존 팀장 전용 정책을 유지합니다. 공유 토큰과 URL은 로그에 출력하지 않습니다.

## 상태 정의

| status | 의미 | expiresAt |
|---|---|---|
| ACTIVE | 현재 사용 가능 | 영구 링크는 null, 과거 7일형은 만료 시각 |
| REVOKED | 팀장이 폐기한 링크 | 유형에 따른 값 유지 |
| EXPIRED | 폐기되지 않았으나 유효기간이 지난 과거 7일형 링크 | 만료 시각 |

폐기는 만료보다 우선합니다. 현재 생성하는 영구 링크는 자연 만료되지 않습니다. DB의 레거시 NOT NULL 만료일이 지나도 tokenSeed가 있는 영구 링크는 ACTIVE이며 응답 expiresAt은 null입니다. EXPIRED는 과거 데이터가 있다면 실제 목록 응답에 나올 수 있습니다. 만료/폐기 링크의 미리보기 및 가입은 기존처럼 HTTP 410입니다. `/current`는 활성 영구 링크만 반환하므로 성공 응답 status는 ACTIVE입니다.

과거 해시 전용 토큰은 역산하지 않습니다. 현재 영구 링크가 없는 경우 팀장이 새 영구 링크를 생성해야 합니다. 서버 시크릿 변경으로 저장된 토큰 해시와 복원 토큰이 다르면 503으로 실패하며 임의로 새 주소를 반환하지 않습니다. `invitation.secret`은 기존 운영 값을 유지해야 합니다.

## URL 설정과 배포

현재 공개 프런트의 라우팅 `/invite/:token`과 공유 함수의 `/invite/${encodeURIComponent(token)}` 경로를 읽기 전용으로 확인했습니다. 기본 공유 주소는 `https://savanna-frontend.vercel.app/invite/{token}`입니다. 배포 프런트와 로컬 프런트가 같은 백엔드를 사용하면 같은 운영 주소를 받습니다.

필요한 경우 `TEAM_INVITATION_URL_TEMPLATE`으로 명시적으로 변경할 수 있습니다. 예를 들어 로컬 백엔드 전용 테스트에서는 `http://localhost:5173/invite/{token}`입니다. 공유 서버에 로컬 주소를 설정하면 모든 사용자에게 그 주소가 반환되므로 운영 서버는 기본값을 유지합니다. 허용 값은 `{token}`을 포함한 HTTP(S) 주소입니다.

새 DB 컬럼/마이그레이션은 필요하지 않습니다. 기존 초대 seed/hash를 이용합니다. EC2나 실제 앱 DB는 변경하지 않았습니다. 새 JAR 배포·재시작 후 Swagger를 새로고침하고 모든 팀원의 조회 주소가 같은지 확인합니다.

검증: 전체 191개 테스트 통과(실패/오류 0), bootJar 성공. 팀장/팀원의 동일 URL 재조회와 조회 무변경, 권한 경계, 폐기 후 조회 차단·새 링크 생성, 과거 EXPIRED/410 및 영구 ACTIVE/null 만료일, 목록 토큰 노출 제한, Swagger 상태 enum과 200/410 응답을 검증했습니다.
