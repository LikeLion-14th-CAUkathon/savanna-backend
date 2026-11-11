# ContestKorea 공고 수집

## 확정된 데이터 정책

- 접수 기간 STR_DATE1의 시작 연도는 2026년으로 고정합니다. 실행 시점의 연도나 공고 ID로 추측하지 않습니다.
- 마감 월일이 시작 월일보다 앞서면 마감 연도만 2027년입니다.
- `10.30~11.18` → 2026-10-30 ~ 2026-11-18.
- `11.18-01.23` → 2026-11-18 ~ 2027-01-23.
- 동일 월일은 같은 날 접수입니다. 실제 달력에 없는 날짜와 알 수 없는 형식은 저장하지 않고 skippedIds로 보고합니다.
- 마감은 날짜만 저장하며 당일까지 유효합니다. 접수 시작은 기존 LocalDateTime과 호환하기 위해 시작일 00:00을 내부 기준으로 저장합니다. 주최 측의 실제 시작 시각을 의미하지 않습니다.
- STR_AREA는 **참가 가능 지역**입니다. participationRegion에 원문을 저장하며 기존 개최 지역 region과 구분합니다.
- 온라인·전국 외의 지역 조건은 현재 팀원에게 거주 지역 필드가 없어 자동 비교하지 않고 PARTICIPATION_REGION / TEAM_INFO_REQUIRED로 안내합니다.
- STR_TARGET에 `해당자`가 있으면 ADDITIONAL_REQUIREMENTS / TEAM_INFO_REQUIRED 및 ‘공고 상세 원문을 확인해야 합니다’ 메시지를 반환합니다. 팀 정보가 없어도 이 안내를 포함합니다.
- 명확한 인원 불일치가 함께 확인되면 기존 정책대로 전체 상태는 MISMATCH이며, 원문 확인 필요 항목도 함께 반환합니다.
- 대학생이나 복잡한 대상 나열을 재학생·제한 없음으로 임의 변환하지 않습니다.

## 설정

기존 application-secret.yml의 contest-korea.base-url, contest-korea.api-key를 사용합니다. 키는 소스·테스트·문서에 저장하지 않습니다.
키가 없는 환경도 앱은 기동되며, 수집을 호출할 때 명시적으로 미설정 오류가 발생합니다.

## 실행 지점

로컬 앱을 IntelliJ에서 정지한 뒤 프로젝트 루트 PowerShell에서 실행합니다. 파일 H2 DB는 실행 중인 앱과 별도 프로세스가 동시에 열 수 없습니다.

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea -PcontestKoreaCategory=IDEA --no-daemon
```

- 기존 설정의 현재 DB를 사용합니다. 테스트용 DB로 바꾸지 않습니다.
- 파일 H2 DB는 앱 기동/스키마 갱신 전에 독점 잠금을 확인하고 tmp/db-backups에 백업합니다. DB가 사용 중이면 수집 전 종료합니다. 원본 DB 파일이 없으면 오류를 반환하며 빈 DB를 만들지 않습니다.
- 새 앱은 localhost의 임의 포트에만 바인딩합니다. 8080 포트를 차지하지 않습니다.
- application-secret.yml의 설정을 읽습니다. API 키나 JWT를 인자·결과에 출력하지 않습니다.
- 수집이 커밋된 뒤 임시 검증 사용자를 생성하여 실제 GET /api/v1/announcements를 JWT로 조회합니다. 조회가 끝나면 임시 사용자를 삭제합니다.
- 기본 목록에서 노출 가능한 수집 공고(공개·마감 전 또는 마감 당일)가 모두 포함되는지 페이지를 순회하여 확인합니다. 마감·비공개 공고는 노출 기대값에서 제외합니다.
- 작업 후 앱과 DB 연결을 닫고 종료하므로 원래 앱을 다시 실행하면 됩니다.
- 공개 수집 API·일반 앱 기동 시 자동 수집·스케줄러는 등록하지 않았습니다.
- 네트워크 조회는 트랜잭션 밖, 저장·갱신은 별도 트랜잭션입니다. 저장 트랜잭션 실패 시 전체 저장이 롤백됩니다.
- CONTEST_KOREA_RESULT에서 created/updated/skipped/failedItems/failedRequests를 확인합니다. startupFailed는 수집 전 앱/DB 기동 실패이고 항목 저장 실패와 구분됩니다.
- 외부 조회 실패 시 전체 항목 개수를 알 수 없어 failedItems=unknown입니다. 저장 실패 시 해당 배치 전체를 실패로 보고합니다.
- CONTEST_KOREA_API_VERIFIED는 실제 HTTP 검증 성공입니다. 검증 실패는 수집 롤백을 의미하지 않습니다.
- 종료 코드 0=수집 및 검증 성공, 1=기동·수집 실패, 2=수집은 커밋됐지만 API 검증 실패입니다.

### 기존 DB를 변경하지 않는 백업·접속 진단

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea -PcontestKoreaBackupOnly=true --no-daemon
```

이 모드는 원본 DB 파일을 백업하고, 설정된 사용자/비밀번호로 **백업 파일에만** IFEXISTS=TRUE;ACCESS_MODE_DATA=r를 사용해 읽기 전용 접속을 검증합니다.
원본에 JDBC 접속하지 않으며 JPA 초기화·DDL·외부 API 호출·공고 수집을 실행하지 않습니다. 성공 시 BACKUP_CONNECTION_VERIFIED와 BACKUP_ONLY_COMPLETE가 출력됩니다.
암호화되지 않은 현재 H2 파일 설정을 대상으로 합니다. CIPHER 등 별도 파일 암호화 옵션을 사용하는 환경에는 추가 설정 전달이 필요합니다.

백업 폴더는 contest-korea.import.backup-directory 설정으로 변경할 수 있으며 기본값은 프로젝트 루트의 tmp/db-backups입니다.

### Windows 백업 오류 수정

기존 코드는 원본 파일의 독점 잠금을 획득한 뒤 Files.copy로 원본을 다시 열었습니다. Windows의 강제 파일 구간 잠금 때문에 자기 자신이 잡은 잠금과 충돌하여 FileSystemException이 발생했습니다.
수정 후에는 잠금을 소유한 동일 FileChannel에서 읽고, 새 백업 채널에 쓰고 flush한 다음 잠금을 해제합니다. 원본 채널에 쓰기·truncate를 수행하지 않습니다.

실패 시 원본 cause와 suppressed 예외를 보존합니다. CONTEST_KOREA_FAILURE에는 다음 안전한 필드만 출력합니다.

- stage: OPEN_SOURCE / LOCK_SOURCE / CREATE_BACKUP_DIRECTORY / CREATE_BACKUP_FILE / COPY_BACKUP / FLUSH_BACKUP / RELEASE_LOCK
- 실제 원본 DB·백업 디렉터리 경로, 예외 클래스의 연결 관계
- SQLException 및 nextException의 SQLState·errorCode
- SQLException이 없는 파일 I/O 오류는 SQLState=N/A, errorCode=N/A

비밀번호나 API 키가 포함될 수 있는 원본 예외 메시지·SQL·접속 URL은 그대로 출력하지 않습니다. 원본 예외 객체는 cause로 보존되므로 디버거에서 필요한 값을 선택해 확인할 수 있습니다.
파일 권한 오류와 파일 잠금 오류를 구분하여 진단하며, 권한이나 잠금을 강제로 우회하지 않습니다.
수동 명령은 Hibernate create/create-drop/drop 설정을 거절하고 SQL 초기화도 비활성화합니다. 실제 수집 모드의 기존 update 스키마 갱신은 유지합니다.

내부 호출 지점은 `ContestKoreaImportService.sync(ContestKoreaCategory.IDEA)`입니다.

현재 명시적인 분야 매핑:

| 범주 | 외부 코드 | 앱 분야 |
|---|---|---|
| IDEA | 031410001 | IDEA_PLANNING |
| IT | 030310001 | DEVELOPMENT_HACKATHON |
| DESIGN | 030610001 | DESIGN |

이는 외부의 넓은 분야를 앱의 분류에 연결한 초기 규칙입니다. IT에는 학문·과학도 포함되므로 모두 해커톤인 것은 아닙니다. 외부 분야 원문은 externalCategory에 보존합니다. 사회·ESG를 제목만 보고 임의 분류하지 않습니다.

## 저장과 갱신

- sourceProvider=CONTEST_KOREA + externalId=STR_MEETCODE의 유니크 제약으로 중복을 방지합니다.
- 제목·주최기관 공백을 정리하고 접수 기간 원문·외부 분야·참가 가능 지역·포스터·수집 시각을 저장합니다.
- 동일 ID는 기존 공고를 갱신하고 공고 ID를 유지합니다.
- 기존 공개 여부와 외부에 없는 제출물·심사기준·지원 링크·인원 조건 등 보강 데이터는 유지합니다.
- 잘못된 날짜나 필수값·URL이 있는 항목은 skippedIds로 보고하고 기존 공고를 덮어쓰지 않습니다.
- 응답에 없는 공고를 삭제하거나 마감 처리하지 않습니다. 최신 목록 누락이 삭제를 의미하지 않기 때문입니다.
- STR_URL은 sourceUrl, STR_IMG는 posterUrl로 저장합니다. STR_URL을 실제 지원 링크 applyUrl로 복사하지 않습니다.
- INT_FREEYN·STR_SERVICE는 의미가 확정되지 않아 자격이나 공개 여부 판정에 쓰지 않습니다.
- 상세 응답에 participationRegion, posterUrl, applicationPeriodRaw를 추가했습니다.
- HTTP/파싱 실패 예외에 API 키가 포함된 원본 URL이나 외부 응답 본문을 첨부하지 않습니다.

연도 규칙은 이번 2026 공고 수집 정책입니다. 2027년 이후 신규 모집 수집 전에 정책을 변경해야 합니다.
