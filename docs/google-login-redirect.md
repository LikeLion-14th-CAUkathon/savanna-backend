# 구글 로그인 성공 리다이렉트

로그인 성공 시 Refresh Token을 기존 HttpOnly 쿠키로 발급한 뒤 프런트 로그인 페이지로 이동합니다. URL에는 토큰을 담지 않습니다. 로그인 성공 핸들러의 기존 `/login-success` 이동을 대체합니다.

로컬 기본 주소: `http://localhost:5173/login?google=success`

배포 환경에서는 백엔드 프로세스에 다음 환경변수를 설정하고 새 코드를 배포·재시작합니다.

```bash
export OAUTH2_SUCCESS_REDIRECT_URL='https://savanna-frontend.vercel.app/login?google=success'
```

서비스 관리자를 사용한다면 해당 서비스의 환경변수에 등록합니다. 또는 application-secret.yml에 다음 설정을 병합합니다(기존 app 설정이 있으면 하위 키만 추가).

```yaml
app:
  oauth2:
    success-redirect-url: https://savanna-frontend.vercel.app/login?google=success
```

구글 콘솔의 OAuth 리다이렉트 URI는 기존 백엔드 `/login/oauth2/code/google` 주소를 유지합니다. 위 설정은 구글 인증을 백엔드에서 처리한 다음 이동할 프런트 주소입니다.

프런트는 google=success 도착 후 쿠키를 포함해 GET /api/v1/auth/csrf → X-XSRF-TOKEN 헤더와 함께 POST /api/v1/auth/refresh → 발급받은 Access Token으로 GET /api/v1/me 순서로 처리하고 data.onboardingCompleted로 화면을 분기합니다.

초기 리다이렉트 변경 당시 쿠키는 로컬용 SameSite=Lax/Secure=false였습니다. 이후 확인된 localhost 프런트 + HTTPS 배포 API 구성에 대해서는 아래 **배포 API 교차 사이트 조합 수정** 절의 설정을 적용합니다.

이번 작업에서 EC2 설정 변경이나 배포는 실행하지 않았습니다.

## 로컬 403·쿠키 전달 확인 (2026-11-13)

현재 설정은 로컬 HTTP용 Refresh 쿠키 `HttpOnly=true; Secure=false; SameSite=Lax; Path=/api/v1/auth`이며 Domain을 지정하지 않습니다. CORS는 `app.cors.allowed-origins`에 명시된 origin과 credentials 요청을 허용합니다. localhost:5173은 기본 허용 대상입니다.

로컬 프런트와 로컬 API 조합을 검증할 때는 로그인 시작·구글 콜백·CSRF 조회·refresh를 모두 **같은 백엔드 호스트**로 통일합니다. 예: 프런트 `http://localhost:5173`, 백엔드 `http://localhost:8080`. 로그인은 localhost에서 하고 API 호출은 127.0.0.1 또는 배포 주소로 보내면 동일한 쿠키 흐름이 아닙니다. 프런트 실제 포트가 다르면 그 origin을 CORS 허용 목록에 명시해야 합니다.

프런트 예시 (브라우저에서 실행, 프런트에서 관리하는 실제 API 기본 주소에 맞춰 사용):

```js
const API = "http://localhost:8080";

// 로그인 버튼: fetch 대신 브라우저 페이지 이동으로 시작
window.location.assign(`${API}/oauth2/authorization/google`);

// 프런트 /login?google=success 도착 후 아래를 한 번 실행
async function finishGoogleLogin() {
  const csrfResponse = await fetch(`${API}/api/v1/auth/csrf`, {
    credentials: "include",
  });
  if (!csrfResponse.ok) throw new Error(`CSRF 조회 실패: ${csrfResponse.status}`);
  const { csrfToken } = await csrfResponse.json();

  const refreshResponse = await fetch(`${API}/api/v1/auth/refresh`, {
    method: "POST",
    credentials: "include",
    headers: { "X-XSRF-TOKEN": csrfToken },
  });
  if (!refreshResponse.ok) throw new Error(`토큰 재발급 실패: ${refreshResponse.status}`);
  const { data } = await refreshResponse.json();

  const meResponse = await fetch(`${API}/api/v1/me`, {
    headers: { Authorization: `Bearer ${data.accessToken}` },
  });
  if (!meResponse.ok) throw new Error(`내 정보 조회 실패: ${meResponse.status}`);
  const me = await meResponse.json();
  // data.accessToken은 앱의 기존 토큰 관리 방식으로 보관
  return { accessToken: data.accessToken, user: me.data };
}
```

로그인 시작과 콜백 완료 처리는 별도 시점입니다. 예제 전체를 연속 실행하지 않습니다. 콜백 완료 처리는 한 번만 실행하여 Refresh Token 동시 회전을 피합니다.

특히 GET `/api/v1/auth/csrf` 응답은 **`{"csrfToken":"..."}`** 입니다. `data` 래퍼가 없습니다. Axios를 사용한다면 `response.data.csrfToken`이며, CSRF/refresh 요청 모두 `withCredentials: true`가 필요합니다. Axios 인스턴스가 자동으로 CSRF 헤더를 넣을 것이라고 가정하지 말고 응답받은 값을 명시적으로 `X-XSRF-TOKEN`에 넣으세요. refresh 요청에 만료된 Access Token의 Authorization 헤더를 붙이지 않습니다.

| 확인한 경우 | 서버 결과 |
|---|---|
| 유효한 Refresh 쿠키 + CSRF 쿠키 + 일치하는 X-XSRF-TOKEN | 200, Access Token 발급 및 Refresh 쿠키 회전 |
| Refresh 쿠키만 있고 CSRF 미전달 | 403, 토큰 회전 안 함 |
| CSRF 쿠키 누락 또는 헤더 불일치 | 403 |
| CSRF 검증 통과 후 Refresh 쿠키 없음 | 401 |
| 허용하지 않은 origin의 사전 요청 | 403 |

403만으로 Refresh 쿠키가 없다고 결론 내릴 수 없습니다. 개발자 도구 Network에서 OPTIONS와 POST 중 어느 요청이 실패했는지, 응답이 `Invalid CORS request`인지, `/csrf` 요청의 `Set-Cookie: XSRF-TOKEN`과 `/refresh` 요청의 쿠키·헤더가 있는지 확인합니다. 토큰 원문을 로그·메신저로 공유하지 않습니다. Refresh Token은 HttpOnly이므로 `document.cookie`에 보이지 않는 것이 정상입니다. 로그인 화면이 아니라 백엔드 refresh 요청의 Network 쿠키 항목으로 확인하세요.

로컬 프런트에서 **배포 API**를 호출하는 경우는 위의 완전 로컬 구성과 다릅니다. 사이트가 다르면 SameSite=Lax 쿠키가 전송되지 않을 수 있습니다. 배포 API의 HTTPS와 실제 도메인을 확인한 뒤 별도로 쿠키/CORS 설정을 결정해야 하며, HTTP 서버에 SameSite=None만 추가해서 해결할 수 없습니다. 이번 확인에서는 해당 운영 설정이나 EC2를 변경하지 않았습니다.

검증 명령:

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" test --tests '*LocalLoginCookieFlowTest' --no-daemon
```

5개 테스트에서 실제 성공 핸들러의 쿠키 발급/프런트 리다이렉트, 필터 체인의 CSRF·CORS, Refresh Token 회전, Access Token으로 내 정보 조회 및 로그아웃을 검증했습니다. 테스트 DB는 메모리 H2이며 실제 앱 DB를 사용하지 않습니다. 구글 인증은 테스트 OIDC 사용자로 대체했고, 실제 구글 로그인·브라우저의 SameSite/서드파티 쿠키 동작을 검증한 것은 아닙니다. 완전 로컬 HTTP 구성은 기존 Lax/false 정책을 유지하며 배포 정책은 다음 절에서 구분합니다. CSRF 검증은 유지합니다.

동작 근거: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Fetch credentials](https://fetch.spec.whatwg.org/), [MDN 쿠키 속성](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie).

## 배포 API 교차 사이트 조합 수정 (2026-11-13)

대상은 프런트 `http://localhost:5173`, API `https://savannaexpedition.duckdns.org`입니다. 서로 다른 사이트이므로 완전 로컬 검증 결과를 그대로 적용할 수 없습니다.

### 수정 전 배포 서버의 실제 응답 확인

인증 없이 안전한 진단 요청만 전송했으며 실제 사용자 로그인·운영 설정·DB를 변경하지 않았습니다. 쿠키/토큰 원문은 출력하지 않았습니다.

| 요청 | 관측 결과 |
|---|---|
| localhost Origin으로 OPTIONS /api/v1/auth/refresh | 200, Allow-Origin=http://localhost:5173, Allow-Credentials=true, x-xsrf-token 허용 |
| Vercel Origin으로 OPTIONS /api/v1/auth/refresh | 200, 해당 Origin과 Allow-Credentials=true |
| 새 클라이언트로 GET /api/v1/auth/csrf | 200, XSRF-TOKEN 쿠키 속성 Path=/; Secure, **SameSite 없음** |
| CSRF 없는 POST /api/v1/auth/refresh | 403, CORS 거부 응답 아님 |
| HTTP 클라이언트에서 유효한 CSRF 쿠키·헤더를 보내고 Refresh 쿠키 없이 POST | 401 |

따라서 해당 origin의 CORS 허용 자체는 배포 서버에서 이미 정상입니다. 쿠키의 SameSite가 없으면 일반적으로 Lax로 처리되어 cross-site fetch에서 쿠키가 차단될 수 있습니다. 수정 전 Refresh 발급·회전·삭제 코드는 Lax/false를 하드코딩하고 있었습니다. 실제 로그인 콜백의 Refresh 쿠키는 구글 사용자 로그인을 수행하지 않아 직접 관측한 것은 아닙니다.

### 저장소 수정 내용과 배포 설정

AuthCookieFactory로 로그인 성공/Refresh 회전/로그아웃 쿠키 정책을 통합했고, CSRF CookieCsrfTokenRepository에도 같은 정책을 적용했습니다. `application-prod.yml`에서 Secure=true, SameSite=None을 사용합니다. None+Secure=false 또는 잘못된 SameSite 설정은 앱 기동 시 거절합니다.

| 설정 | 완전 로컬 HTTP 기본 | HTTPS 배포 API(prod) |
|---|---|---|
| Refresh SameSite / Secure | Lax / false | None / true |
| Refresh HttpOnly / Path | true / /api/v1/auth | true / /api/v1/auth |
| CSRF SameSite / Secure | Lax / false | None / true |
| CSRF HttpOnly / Path | false / / | false / / |
| Domain | 미지정(host-only) | 미지정(host-only) |

Refresh 쿠키 발급·회전·삭제 모두 동일 속성을 사용합니다. localhost에서 duckdns.org 쿠키를 읽을 필요가 없고 Domain=localhost로 설정하지 않습니다. Secure는 쿠키를 전송하는 대상 API의 HTTPS를 기준으로 하므로 프런트가 HTTP localhost인 것과 양립합니다. TLS를 프록시에서 종료해 내부 요청이 HTTP여도 prod 쿠키는 Secure=true입니다.

백엔드 배포 환경변수 예시 (서비스의 기존 프로필이 있다면 prod를 함께 활성화하거나 아래 쿠키 환경변수를 명시하여 동일 정책을 적용):

```env
SPRING_PROFILES_ACTIVE=prod
AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=None
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://savanna-frontend.vercel.app
OAUTH2_SUCCESS_REDIRECT_URL=http://localhost:5173/login?google=success
```

로컬 프런트로 로그인 완료를 검증할 때의 redirect 예시입니다. 로그인 성공 이동 주소는 서버의 고정 설정이므로 검증 후 배포 프런트로 돌릴 때는 `https://savanna-frontend.vercel.app/login?google=success`로 변경해야 합니다. 구글 콘솔 콜백은 배포 API의 `https://savannaexpedition.duckdns.org/login/oauth2/code/google`입니다. 쿠키를 URL에 담지 않습니다.

새 코드와 설정 배포·재시작 후 **다시 구글 로그인**하여 새로운 정책의 Refresh 쿠키를 발급받습니다. 이번 작업은 저장소 수정과 격리 테스트까지이며 서버 배포·재시작은 수행하지 않았습니다. 기존 배포 서버의 응답이 자동으로 바뀐 것은 아닙니다.

### 프런트 요청

위 finishGoogleLogin 예제의 API를 `https://savannaexpedition.duckdns.org`로 바꾸고 CSRF 조회와 Refresh에 모두 credentials=include를 사용합니다. Axios는 두 요청 모두 withCredentials=true입니다.

```js
const API = "https://savannaexpedition.duckdns.org";
const csrfResponse = await fetch(`${API}/api/v1/auth/csrf`, {
  credentials: "include",
});
if (!csrfResponse.ok) throw new Error(`CSRF 조회 실패: ${csrfResponse.status}`);
const { csrfToken } = await csrfResponse.json();
const refreshResponse = await fetch(`${API}/api/v1/auth/refresh`, {
  method: "POST",
  credentials: "include",
  headers: { "X-XSRF-TOKEN": csrfToken },
});
if (!refreshResponse.ok) throw new Error(`토큰 재발급 실패: ${refreshResponse.status}`);
const { data } = await refreshResponse.json();
```

CSRF 토큰은 localhost의 document.cookie나 Axios 자동 XSRF 복사에 의존하지 않고 GET 응답 본문에서 읽습니다. duckdns.org 쿠키는 localhost JS에서 읽을 수 없습니다. `csrfToken`을 읽었어도 브라우저가 함께 설정된 XSRF-TOKEN 쿠키를 차단했다면 다음 POST는 403입니다.

### 브라우저 쿠키 차단과 403 구분

| 관측 | 판단 |
|---|---|
| OPTIONS 403 / Invalid CORS request | 허용 origin·method·header 문제 |
| OPTIONS 200, GET csrf 200이나 Set-Cookie에 차단 사유 표시 | 브라우저 쿠키 저장 정책 문제. SameSite/Secure/서드파티 차단 확인 |
| POST 403, XSRF-TOKEN 쿠키 누락 또는 CSRF 헤더 누락/불일치 | CSRF 검증 실패. Refresh가 있어도 발생 |
| 유효한 CSRF와 Authorization 없는 POST가 401 | Refresh 쿠키 누락·만료·무효화 등의 인증 문제 |
| None+Secure와 credentials를 맞춰도 쿠키가 차단됨 | 브라우저 서드파티 쿠키 정책 확인 필요. CORS 허용만으로 해결되지 않음 |

Network의 Cookie/Set-Cookie 차단 사유를 확인해야 실제 브라우저의 개별 차단 원인을 확정할 수 있습니다. 필요시 인증 콜백과 쿠키 발급까지 포함하는 동일 사이트 도메인 또는 BFF/프록시 구성을 검토합니다. 단순히 fetch URL만 프록시로 바꾸는 것으로 기존 로그인 쿠키가 이동하지 않습니다.

CrossSiteLoginCookieFlowTest는 prod 정책에서 로그인 쿠키·CSRF 발급·회전·삭제 속성, CSRF 403/인증 401 구분, localhost/Vercel CORS 및 잘못된 설정 거부를 검증합니다. 실제 브라우저의 서드파티 쿠키 허용을 보장하는 테스트는 아닙니다.
