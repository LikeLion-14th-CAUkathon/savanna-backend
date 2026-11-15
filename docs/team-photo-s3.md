# 비공개 S3 팀 사진

## API와 프런트 연결

1. 기존 `POST /api/v1/teams`로 팀을 생성합니다. `photoUrl`은 생략합니다.
2. 팀 생성 응답의 `data.teamId`로 아래 업로드를 호출합니다. 생성이 성공하고 업로드만 실패했다면 팀을 다시 생성하지 말고 업로드만 재시도합니다.

```javascript
const form = new FormData();
form.append('file', selectedFile);
const response = await fetch(`${API_BASE}/api/v1/teams/${teamId}/photo`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${accessToken}` },
  body: form
});
// Content-Type은 직접 지정하지 않습니다. 브라우저가 boundary를 설정합니다.
if (!response.ok) throw new Error('사진 업로드 실패');
const { data } = await response.json();
// data.photoUrl: 화면 표시용 URL, data.expiresInSeconds: 600
```

인증은 기존 Access Token을 사용합니다. 팀장만 업로드·교체·제거할 수 있으며 다른 팀원은 조회만 가능합니다. 팀 목록·상세·수정 응답의 `photoUrl`에 새 서명 URL이 반환됩니다. URL을 DB/로컬 저장소에 영구 저장하지 말고 만료되면 팀 조회를 다시 호출합니다. URL을 가진 사람은 만료까지 접근할 수 있으므로 로그·공유에 노출하지 않습니다.

사진 제거는 기존 `PATCH /api/v1/teams/{teamId}`에 `{"photoUrl":""}`을 보냅니다. 생략/null은 유지합니다. 임의 URL 직접 등록은 400으로 거부합니다. `photoUrl` 응답 필드는 유지되지만 URL 직접 저장 요청은 중단합니다.

제한: 실제 내용이 JPEG/PNG이고 5 MiB 이하, 한 변 6000px 이하 및 총 1200만 픽셀 이하. 파일명·요청 MIME은 신뢰하지 않습니다. 디코딩 후 재인코딩하여 EXIF/위치 정보 등을 제거하며 결과도 5 MiB 이하로 제한합니다. SVG/GIF/WebP/HEIC는 415, 손상 이미지/해상도 초과는 400, 용량 초과는 413입니다. 휴대폰 HEIC는 업로드 전에 JPEG로 변환해야 합니다. 파일 누락은 400, 미인증 401, 권한 부족 403, 팀 없음 404, 저장소 비활성/실패는 503입니다.

## 저장 및 AWS 인증

새 사진은 `team-photos/{teamId}/{UUID}.jpg|png`에 저장합니다. DB `team.photo_object_key`에 객체 키만 저장하며 `photo_url`은 null로 비웁니다. 조회 URL 수명은 10분입니다. S3 ACL을 지정하거나 공개 정책을 추가하지 않습니다.

AWS SDK `InstanceProfileCredentialsProvider`만 사용합니다. 환경 변수/설정 파일의 정적 액세스 키는 사용하지 않습니다. EC2 인스턴스 역할에 버킷의 `team-photos/*` 대상 `s3:PutObject`, `s3:GetObject` 권한이 필요합니다. 버킷 퍼블릭 액세스 차단을 유지합니다. SSE-S3 기본 암호화를 권장하며 별도 KMS 키 사용 시 KMS 권한도 필요합니다. EC2의 IMDS 접근이 가능해야 합니다. 로컬 실행은 기본 비활성화이며 키를 추가하지 않습니다.

프런트가 S3로 직접 업로드하지 않으므로 업로드용 S3 CORS 설정은 필요하지 않습니다. 기존 백엔드 CORS에서 프런트 Origin과 Authorization 헤더를 허용해야 합니다.

## 배포 순서 (이번 작업에서는 EC2에 적용하지 않음)

1. 새 JAR을 별도 경로에 업로드합니다. 기존 운영 `application-secret.yml`은 유지합니다.
2. `sudo systemctl stop savanna`로 정상 종료합니다. 종료 실패/DB 점유 시 강제 종료하지 않습니다.
3. `/home/ubuntu/savanna-backend/data/savanna.mv.db`와 기존 JAR을 `build` 밖의 권한 제한 백업 폴더에 복사합니다.
4. 기존 운영 DB 접속 설정으로 `docs/sql/team-photo-object-key-h2.sql`을 실행합니다. 새 컬럼은 nullable이며 기존 팀을 변경/삭제하지 않습니다. 마지막 조회가 반환하는 팀은 기존 URL을 검토하고 필요하면 재업로드합니다. 알 수 없는 URL을 객체 키로 추측 변환하지 않습니다.
5. `sudo systemctl edit savanna`에서 기존 설정에 다음 항목을 추가합니다.

```ini
[Service]
Environment="TEAM_PHOTO_ENABLED=true"
Environment="TEAM_PHOTO_BUCKET=savanna-team-photos-20261114"
Environment="TEAM_PHOTO_REGION=ap-northeast-2"
```

6. Nginx가 있다면 해당 API의 server/location에 `client_max_body_size 6m;`을 설정하고 `sudo nginx -t` 성공 후 reload합니다. 앱 multipart 제한은 파일 5MB/전체 요청 6MB입니다. Nginx에서 먼저 차단하면 앱 JSON 대신 Nginx 413이 반환될 수 있습니다.
7. 백업한 기존 JAR 위치를 기록하고 새 JAR로 교체합니다. `sudo systemctl daemon-reload` 후 `sudo systemctl start savanna`를 실행합니다.
8. 팀장 토큰으로 사진 업로드 → 팀 재조회 → 화면 이미지 표시 → DB에 URL 대신 객체 키 저장 확인을 진행합니다. 비로그인/일반 팀원의 업로드는 거부되는지, S3 직접 비서명 URL은 비공개로 차단되는지도 확인합니다. 서명 URL이나 토큰은 출력/로그하지 않습니다.

레거시 `photo_url`은 기존 호환을 위해 남겨 두며 새 사진 업로드 시 null로 교체합니다. 기존 임시 URL을 DB에서 객체 키로 자동 변환하지 않습니다. 조회 시 쿼리 문자열이 포함된 레거시 URL은 반환하지 않으며 재업로드해야 합니다.

교체/제거한 기존 S3 객체 및 DB 저장 실패 후 남은 객체는 자동 삭제하지 않습니다. 현재 IAM 권한에 DeleteObject가 없으므로 운영 중 별도 미참조 객체 정리 절차가 필요합니다. 모든 `team-photos/` 객체에 일괄 만료 수명주기를 걸면 현재 사용 중인 사진도 삭제되므로 피합니다.

## 검증 범위

자동 테스트는 모의 저장소로 인증·팀장 권한, 실제 이미지 검사, 객체 키 저장과 응답 URL 재발급, 저장소 실패 시 기존 사진 보존을 검증합니다. 실제 EC2 IAM/S3 업로드와 브라우저 표시는 배포 후 검증 대상입니다. 테스트에서는 운영 DB나 실제 S3를 변경하지 않습니다.

검증 결과: 전체 회귀 테스트 173개 성공(실패/오류 0), 이후 추가한 S3 어댑터 테스트 1개 성공. `bootJar` 성공. 어댑터 테스트는 대상 버킷·객체 키·콘텐츠 형식·공개 ACL 미설정·서명 유효기간 10분을 검증합니다. DB 마이그레이션은 메모리 H2에서 반복 적용 및 기존 행 보존을 검증했습니다.

AWS 참고: [EC2 역할 자격 증명](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-providers.html), [S3 임시 조회 URL](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html).
