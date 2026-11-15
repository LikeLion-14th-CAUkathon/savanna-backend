# Vercel 프록시 기반 Google 로그인

## 코드 반영

prod 기본 Google 콜백은 `https://savanna-expedition.vercel.app/login/oauth2/code/google`, 성공은 `/login?google=success`, 실패는 `/login?google=fail`로 고정합니다. 성공/실패 URL에 토큰이나 공급자의 오류 원문을 추가하지 않습니다. 코드의 OAuth 인증/CSRF/state 검증은 유지합니다.

`server.forward-headers-strategy=framework`를 prod에 추가했습니다. 콜백은 요청 Host나 X-Forwarded-Host로 추정하지 않고 ClientRegistration의 명시적 URL을 사용합니다. 성공/실패 이동도 설정된 절대 URL을 사용합니다.

| 쿠키 | Domain | Path | Secure(prod) | SameSite(prod) | HttpOnly |
|---|---|---|---|---|---|
| refreshToken | 미지정(host-only) | /api/v1/auth | true | None | true |
| XSRF-TOKEN | 미지정(host-only) | / | true | None | false |
| JSESSIONID(OAuth state 저장용 세션) | 미지정(host-only) | / | true | Lax | true |

Refresh/CSRF의 기존 None+Secure 정책은 유지합니다. 동일 출처 프록시에서도 사용할 수 있습니다. OAuth 세션의 Lax는 Google에서 돌아오는 최상위 GET 콜백에 사용할 수 있습니다. 세션 쿠키의 Domain 설정은 추가하지 않았습니다. 외부 운영 설정에 SERVER_SERVLET_SESSION_COOKIE_DOMAIN 또는 server.servlet.session.cookie.domain이 있다면 제거해야 합니다. Refresh/CSRF 생성 코드에는 backend Domain 고정이 없습니다.

브라우저는 Vercel 주소로 요청하고 프록시 응답의 host-only 쿠키를 Vercel 호스트에 저장해야 합니다. OAuth 시작과 콜백 모두 같은 프록시 도메인을 사용해야 세션과 state를 이어갈 수 있습니다. 프록시가 Cookie/Set-Cookie 및 리다이렉트 응답을 전달하는 실제 동작은 배포 후 브라우저에서 확인해야 합니다. 인증 응답은 no-store로 설정했습니다.

기존 백엔드 도메인에 저장된 쿠키는 새 프런트 도메인으로 자동 이전되지 않습니다. 배포 후 Vercel 사이트에서 다시 로그인해야 합니다. forwarded host 기준 같은 출처로 판정되는 프록시 요청에는 CORS 응답 헤더가 생략될 수 있으며, 그것만으로 실패라고 판단하지 않습니다.

## 프런트 예시

프런트 저장소의 vercel.json에 다음 rewrites를 SPA 규칙보다 먼저 넣습니다. **중첩 경로 처리를 위해 세 규칙 모두 `:path*`를 사용합니다.**

```json
{
  "rewrites": [
    {"source":"/api/:path*","destination":"https://savannaexpedition.duckdns.org/api/:path*"},
    {"source":"/oauth2/:path*","destination":"https://savannaexpedition.duckdns.org/oauth2/:path*"},
    {"source":"/login/oauth2/:path*","destination":"https://savannaexpedition.duckdns.org/login/oauth2/:path*"},
    {"source":"/:path*","destination":"/index.html"}
  ]
}
```

배포 API baseURL은 빈 문자열로 두고 기존 `/api/v1/...` 경로를 사용합니다. 로그인은 `window.location.assign('/oauth2/authorization/google')`처럼 최상위 페이지 이동으로 시작합니다. 백엔드 도메인으로 직접 이동하거나 OAuth 시작을 axios 요청으로 처리하지 않습니다.

CSRF GET `/api/v1/auth/csrf` → 응답 csrfToken 및 쿠키 확인 → POST `/api/v1/auth/refresh` 시 X-XSRF-TOKEN 헤더와 쿠키를 함께 보냅니다. withCredentials: true를 유지하고 Refresh Token은 JS에서 읽지 않습니다. OAuth/API 응답에 공개 CDN 캐시를 켜지 않습니다.

## Google Cloud Console (아직 적용하지 않음)

Google Auth Platform의 Clients(또는 API 및 서비스 → 사용자 인증 정보)에서 현재 운영 Google OAuth 웹 클라이언트를 선택하고 승인된 리디렉션 URI에 아래 값을 **정확히** 추가합니다.

```text
https://savanna-expedition.vercel.app/login/oauth2/code/google
```

기존 주소는 다른 환경이 사용 중인지 확인하기 전 삭제하지 않습니다. 로그인 성공/실패 프런트 주소는 OAuth 콜백이 아니므로 위 리디렉션 URI와 혼동하지 않습니다. 클라이언트 ID/secret은 기존 운영 값을 유지하며 프런트에 secret을 전달하지 않습니다.

## EC2 설정 (아직 적용하지 않음)

기존 운영 config/application-secret.yml과 DB는 보존합니다. 새 JAR 배포 시 `sudo systemctl edit savanna`의 기존 [Service] 설정에 다음 공개 설정을 반영합니다.

```ini
[Service]
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://savanna-expedition.vercel.app/login/oauth2/code/google"
Environment="APP_OAUTH2_SUCCESS_REDIRECT_URL=https://savanna-expedition.vercel.app/login?google=success"
Environment="APP_OAUTH2_FAILURE_REDIRECT_URL=https://savanna-expedition.vercel.app/login?google=fail"
Environment="APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://savanna-expedition.vercel.app,https://savanna-frontend.vercel.app"
Environment="AUTH_COOKIE_SECURE=true"
Environment="AUTH_COOKIE_SAME_SITE=None"
Environment="SERVER_FORWARD_HEADERS_STRATEGY=framework"
```

위 예시는 직접 바인딩 환경 변수로 외부 YAML보다 우선 적용합니다. 같은 이름의 기존 항목은 위 주소로 교체하고, 별칭 GOOGLE_OAUTH_REDIRECT_URI/OAUTH2_SUCCESS_REDIRECT_URL/OAUTH2_FAILURE_REDIRECT_URL도 사용 중이면 혼동되지 않게 정리합니다. 외부 설정은 코드 기본값을 덮어쓸 수 있으므로 새 JAR만 교체하면 기존 잘못된 설정이 남을 수 있습니다. 기존 Domain 고정 설정도 제거합니다. systemctl Environment 전체 출력에는 비밀값이 있을 수 있으니 공유하지 않습니다.

프런트 rewrites 배포 및 Console URI 등록 → 기존 JAR 백업·새 JAR 교체 → 위 공개 환경 변수 갱신 → 아래 명령으로 반영합니다. 이번 변경 자체에는 DB 마이그레이션이 필요 없습니다. 이전 작업의 미적용 DB 마이그레이션은 별도로 확인해야 합니다.

```bash
sudo systemctl daemon-reload
sudo systemctl restart savanna
sudo systemctl is-active savanna
```

Nginx 및 Vercel의 실제 운영 설정은 이번 작업에서 읽거나 수정하지 않았습니다. 중간 프록시가 Forwarded/X-Forwarded-*를 어떻게 설정하는지 운영에서 확인해야 합니다. forwarded 헤더는 신뢰하는 프록시 경계에서 처리하고, 임의 클라이언트 헤더를 그대로 신뢰하도록 열지 않습니다. 명시적 콜백 설정은 Host 헤더가 backend 도메인이더라도 Google redirect_uri가 frontend 도메인이 되게 합니다.

## 로컬 환경

prod 백엔드의 콜백을 Vercel로 고정한 상태에서 localhost에서 OAuth를 직접 시작하면, 시작 쿠키는 localhost에 저장되고 콜백은 Vercel로 가므로 동일한 세션을 유지할 수 없습니다. CORS에 localhost가 있다고 해결되는 문제가 아닙니다.

이번 prod 로그인 검증은 Vercel 사이트에서 진행합니다. 순수 로컬 검증은 로컬 백엔드 + Vite 프록시로 OAuth 시작·콜백·API를 모두 같은 localhost:5173에서 처리하고, 로컬 프로세스의 GOOGLE OAuth redirect-uri를 `http://localhost:5173/login/oauth2/code/google`로 명시해야 합니다. 이 주소도 Console의 별도 승인 URI로 등록합니다. 로컬 서버는 prod 프로필을 사용하지 않고 기존 Secure=false/Lax 정책을 사용합니다. 공유 prod 서버를 로컬 콜백으로 바꾸지는 않습니다.

로컬 콜백 환경 변수의 정확한 이름은 `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI`입니다. `GOOGLE_OAUTH_REDIRECT_URI` 별칭은 이번 prod YAML에서 사용하므로 로컬 프로필에서는 표준 Spring 환경 변수를 사용합니다.

## 배포 후 검증

1. Vercel의 `/oauth2/authorization/google` 응답이 index.html이 아니라 Google 이동인지 확인합니다.
2. Google 요청의 redirect_uri가 위 Vercel 콜백과 일치하는지 확인합니다.
3. JSESSIONID가 Vercel 도메인·Path=/·Secure·HttpOnly·SameSite=Lax로 저장되는지 확인합니다. 값은 공유하지 않습니다.
4. Google 성공 후 프런트 success 주소, 취소 시 fail 주소로 이동하는지 확인합니다.
5. refreshToken과 XSRF-TOKEN이 Vercel에 저장되고 /api/v1/auth/refresh에 쿠키와 X-XSRF-TOKEN이 전달되는지 확인합니다.
6. CSRF 누락은 403, CSRF가 유효하나 refreshToken이 없으면 401로 구분합니다. state/세션 유실은 OAuth 실패이며 API의 403과 별개입니다.

자동 테스트는 메모리 DB 및 내장 서버를 사용합니다. 실제 Google 코드 교환·Console 등록·Vercel 전달·브라우저 저장은 별도 검증 대상입니다.

검증 결과: 전체 195개 테스트 통과(실패/오류 0), bootJar 성공. 추가 4개 테스트는 명시적 콜백, 실제 내장 서버의 JSESSIONID 속성과 세션/state 연속성(취소 응답의 access_denied 확인), 고정 성공/실패 이동, Refresh/CSRF 쿠키 속성, 새 Origin의 CORS 및 403/401 구분을 검증했습니다. 기존 로컬/교차 출처 로그인 테스트도 통과했습니다.

참고: [Spring OAuth 콜백 설정](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/login/core.html), [Spring forwarded 설정](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html), [Vercel 외부 rewrites](https://vercel.com/docs/routing/rewrites).
