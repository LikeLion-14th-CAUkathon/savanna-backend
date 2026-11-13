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

이 변경은 성공 후 이동 주소만 변경합니다. 배포에서 프런트와 API가 서로 다른 사이트라면 현재 refreshToken의 SameSite=Lax/Secure=false 설정으로 쿠키를 보내지 못할 수 있으므로 실제 API 도메인·HTTPS 기준 쿠키 설정 확인이 별도로 필요합니다. 프런트 배포 origin의 CORS 허용과 credentials 설정도 확인해야 합니다.

이번 작업에서 EC2 설정 변경이나 배포는 실행하지 않았습니다.
