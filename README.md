# 🦁 사바나원정 Backend

2026 멋쟁이사자처럼 중앙대학교 14기 중커톤 **라이온 퀸** 팀의 사바나원정 백엔드 레포지토리입니다.

해커톤·프로젝트 종료 후에도 팀이 새로운 공모전과 도전을 이어갈 수 있도록 **팀 관리, 공고 탐색, 원정 준비, 참여 제안 및 알림** 기능을 제공합니다.

## 🛠️ Tech Stack

| Category | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.x |
| Build Tool | Gradle (Gradle Wrapper) |
| Web / API | Spring Web, REST API |
| Security | Spring Security, Google OAuth2 / OIDC, JWT |
| Persistence | Spring Data JPA, Hibernate |
| Database | H2 (로컬 설정 기준) |
| API Documentation | Swagger / OpenAPI |
| Testing | JUnit 5, Spring Boot Test, MockMvc |
| Collaboration | Git, GitHub, Pull Request |

## ✨ Key Features

- **사용자 인증 및 프로필**: Google 소셜 로그인, Access/Refresh Token, 사용자 프로필 및 캐릭터 관리
- **팀 관리**: 팀 생성·수정·조회, 팀원 관리, 팀장 승계, 팀 탈퇴
- **초대 링크**: 팀 초대 링크 발급·미리보기·수락 및 신규 팀원 합류 처리
- **공고 및 원정**: 공고 조회, 원정 상세·진행 단계·일정·변경 이력 관리
- **참여 제안**: 제안 송수신, 합류(`JOINED`)·쉬어가기(`DECLINED`) 응답, 읽음·알림 처리
- **팀 변경 시 데이터 범위 관리**: 현재 팀 기준 제안·알림 조회, 탈퇴자의 응답 집계 제외 및 기존 응답 기록 무효화
- **제안 생성 제한**: 팀 전체 제안 최대 6개, 초과 시 `409 Conflict` 및 `PROPOSAL_LIMIT_EXCEEDED` 반환

## 🚀 Getting Started

### 1. Repository Clone

```bash
git clone https://github.com/LikeLion-14th-CAUkathon/savanna-backend.git
cd savanna-backend
git switch develop
```

### 2. Prerequisites

- **JDK 17** 설치
- Git 설치
- Google OAuth2 클라이언트 및 JWT 비밀키 등 **로컬 실행용 환경 설정값** 준비

> `application-secret.yml` 등 비밀 설정 파일은 Git에 커밋하지 않습니다. 실제 설정 항목과 프로파일은 `src/main/resources`의 설정 파일 및 팀 내부 환경 설정 가이드를 확인하세요.

### 3. Build & Test

macOS / Linux:

```bash
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat clean build
```

### 4. Run Development Server

```bash
./gradlew bootRun
```

로컬 기본 서버 주소(별도 포트 설정이 없는 경우): `http://localhost:8080`

> Google 로그인을 실제로 실행하려면 로컬 OAuth 리디렉션 URI와 클라이언트 설정이 일치해야 합니다. DB 및 인증 설정이 준비되지 않으면 실행에 실패할 수 있습니다.

## 🔗 API Overview

아래는 주요 API의 예시입니다. **전체 최신 명세는 실행 중인 서버의 Swagger/OpenAPI 문서와 각 Controller를 기준으로 확인**하세요.

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/v1/expeditions/{expeditionId}` | 원정 상세 정보 조회 |
| `GET` | `/api/v1/expeditions/{expeditionId}/proposals` | 원정의 제안 및 응답 현황 조회 |
| `PATCH` | `/api/v1/expeditions/{expeditionId}/stage` | 원정 진행 단계 변경 |
| `PATCH` | `/api/v1/expeditions/{expeditionId}/schedule` | 원정 일정 수정 |
| `GET` | `/api/v1/proposal-recipients/received` | 받은 제안 목록 조회 |
| `GET` | `/api/v1/proposal-recipients/pending-count` | 미응답 제안 개수 조회 |
| `PATCH` | `/api/v1/proposal-recipients/{proposalRecipientId}/response` | 제안 합류·쉬어가기 응답 |
| `PATCH` | `/api/v1/proposal-recipients/{proposalRecipientId}/read` | 받은 제안 읽음 처리 |
| `GET` | `/api/v1/invitations/{token}` | 초대 링크 미리보기 |

> 인증이 필요한 API에는 유효한 Access Token을 전달해야 합니다. 일부 인증·쿠키 기반 API에는 CSRF 토큰 처리가 필요합니다.

## 🌿 Branch Strategy

- `main` : 안정화 및 배포 브랜치
- `develop` : 개발 통합 브랜치
- `feat/*` : 기능 개발 브랜치
- `fix/*` : 버그 수정 브랜치
- `refactor/*` : 리팩터링 브랜치
- `chore/*` : 환경 설정 및 기타 작업 브랜치

모든 기능 개발은 최신 `develop`을 기준으로 진행하고, Pull Request 및 코드 리뷰를 거쳐 병합합니다. **배포 전에는 `develop → main` PR을 통해 통합 변경 사항을 검토합니다.**

## 📋 Collaboration

1. 작업 전 Issue 등록 및 담당 범위 확인
2. 최신 `develop` 기준으로 작업 브랜치 생성
3. 팀의 커밋 컨벤션에 따라 변경 사항 커밋
4. 빌드 및 관련 테스트 확인
5. `develop` 대상으로 PR 생성 및 코드 리뷰
6. 리뷰 반영 후 병합
7. 배포 시 `develop → main` PR로 통합

## ✅ Testing & Deployment Notes

```bash
./gradlew clean build
```

최근 팀 탈퇴·제안 처리 변경 검증 기록: **241개 테스트 통과, 실패·오류·건너뜀 0개** (2026-11-17 공유 결과 기준). 이 수치는 전체 저장소의 이후 변경까지 검증한다는 의미는 아닙니다.

**배포 전 점검 사항**

- 운영 DB 스키마: `proposal_recipients.revoked_at` nullable 컬럼 적용 여부 확인
- 운영 DB의 실제 마이그레이션/스키마 적용 절차 확인 (`ddl-auto: update`에만 의존하지 않도록 검토)
- 과거 탈퇴자의 기존 제안 수신 기록 소급 처리 정책 검토
- Google OAuth2 리디렉션 URI, JWT 비밀키, CORS 및 쿠키 보안 설정 점검
- 배포 환경에서 로그인 → 토큰 갱신 → 팀 초대 → 제안 조회·응답 흐름 확인

## 🔐 Security

- 비밀키, OAuth 클라이언트 시크릿, JWT 서명키, 운영 DB 접속 정보는 레포지토리에 올리지 않습니다.
- `.gitignore` 및 환경별 설정 분리 여부를 확인합니다.
- 인증·인가가 필요한 API에서는 현재 사용자 및 팀 소속을 검증합니다.

---

Frontend Repository: [savanna-frontend](https://github.com/LikeLion-14th-CAUkathon/savanna-frontend)
