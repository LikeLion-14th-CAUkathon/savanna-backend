# 실제 JWT로 Swagger 테스트하기

LocalSecurityConfig는 제거했습니다. local 프로필에서도 API 인증을 우회하지 않습니다.
SecurityConfig는 Swagger UI 및 OpenAPI 문서 경로만 공개하며, API/JWT/OAuth/CSRF 규칙은 유지합니다.
Swagger 문서 경로 공개는 프로필 제한 없이 적용됩니다.

1. 서버를 재시작합니다. local 프로필은 Swagger 테스트에 필수가 아닙니다.
2. 같은 브라우저에서 http://localhost:8080/oauth2/authorization/google 로 Google 로그인합니다.
3. 현재 로그인 성공 핸들러는 Access Token을 직접 반환하지 않고 HttpOnly Refresh Token 쿠키를 설정합니다.
4. 같은 브라우저의 Swagger 페이지 개발자 도구 Console에서 아래 코드를 실행합니다.
   Google 사이트 콘솔이 아니라 localhost:8080/swagger-ui/index.html에서 실행해야 합니다.

```javascript
const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
if (!csrfResponse.ok) throw new Error('CSRF 토큰 조회 실패');
const { csrfToken } = await csrfResponse.json();
const refreshResponse = await fetch('/api/v1/auth/refresh', {
  method: 'POST',
  credentials: 'same-origin',
  headers: { 'X-XSRF-TOKEN': csrfToken }
});
if (!refreshResponse.ok) throw new Error('토큰 재발급 실패: ' + refreshResponse.status);
const refreshResult = await refreshResponse.json();
```

5. 개발자 도구에서 refresh 요청 Response의 data.accessToken 값을 복사합니다.
6. Swagger의 Authorize를 클릭하고 Bearer 접두사 없이 Access Token 문자열만 입력합니다.
7. GET /api/v1/me 또는 GET /api/v1/announcements를 호출합니다.
8. 토큰이 만료되면 위 재발급 후 Authorize 값을 갱신합니다. 로그아웃 테스트 후에는 Swagger Authorize도 직접 해제합니다.

Swagger의 Authorize는 API 요청에 Authorization: Bearer 헤더를 추가합니다.
Refresh/Logout은 Refresh Token 쿠키와 CSRF 헤더가 필요한 별도 흐름입니다. Bearer 설정만으로 호출할 수 있는 API가 아닙니다.

팀 권한 연동은 별도로 남아 있습니다. ExpeditionTeamAccess 및 EligibilityTeamReader 인터페이스는 유지했습니다.
Team/TeamMember 조회를 연결하기 전에는 정상 JWT가 있어도 원정 서비스는 403, 참가 조건 검사는 TEAM_INFO_REQUIRED를 반환할 수 있습니다.
실제 Google 인증 및 실제 계정의 토큰 재발급은 사용자 브라우저에서 확인해야 합니다. 자동 테스트는 테스트 사용자에 대해 실제 JWT 서명/검증과 필터를 검증합니다.
