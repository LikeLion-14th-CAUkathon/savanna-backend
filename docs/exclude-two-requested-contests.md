# 지정된 외부 공고 2건 제외

> 후속 요청에서 이 두 공고를 포함한 10건의 실제 삭제로 변경됐습니다. 아래는 이전 비공개 처리 기록이며, 최신 처리는 [물리 삭제·CSV 보정 안내](contest-korea-curation.md)를 따릅니다.

## 확인한 사실

| 외부 ID | 공고 | 로컬 announcementId |
|---|---|---|
| 202611040033 | 어린이 미래 상상 그림 공모전 | 297 |
| 202610170003 | 2026 ACEL 전국 청소년 미술 공모전 | 331 |

2026-11-15 로컬 data/savanna.mv.db를 ACCESS_MODE_DATA=r로 읽었습니다. 두 행 모두 source_provider=CONTEST_KOREA이고 exclude_reason에 기존 [YOUTH_ONLY] 사유가 저장돼 있습니다. visibility는 true지만 목록 조건이 exclude_reason IS NULL도 검사하므로 현재 코드에서는 공개되지 않습니다. 상세와 새 추천 후보·저장 추천 조회도 제외 사유를 확인합니다.

공개 운영 Swagger에서 current invitation API와 기존 상세 제외 안내가 있는 것은 확인했습니다. 운영 DB 또는 인증된 운영 목록 응답을 읽은 것은 아닙니다. 운영 DB의 정책 적용 누락 또는 프런트 캐시 여부를 구분해야 합니다. 로컬에 적용한 변경은 EC2 DB에 자동 복제되지 않습니다.

이번에는 일반 청소년 정책이나 분야 분류 로직을 바꾸지 않았습니다. 로컬 실제 DB에도 추가 쓰기를 하지 않았습니다. 사용자 요청으로 이 두 건을 확정 제외하는 운영용 SQL을 별도로 준비했습니다.

## 적용 SQL

`docs/sql/exclude-two-youth-contests-h2.sql`은 CONTEST_KOREA와 두 external_id만 대상으로 visibility=false를 저장하고 [MANUAL_REQUEST] 사유를 추가합니다. 기존 제외 사유를 유지하고 감사 이력을 남깁니다. 재실행해도 같은 사유/감사 이력을 중복 추가하지 않습니다. 공고·원정·제안은 삭제하지 않습니다. API 재수집은 수동 비공개 상태를 되돌리지 않습니다.

## EC2 적용 순서 (아직 실행하지 않음)

이번 데이터 수정만을 위해 새 JAR을 배포할 필요는 없습니다. 기존 배포가 현재 조회 제외 조건을 포함한다는 전제이며, SQL은 visibility도 false로 지정합니다. 이전 요청대로 EC2에 직접 접속하거나 변경하지 않았습니다.

로컬 PowerShell에서 SQL만 업로드합니다. PEM 경로는 실제 파일로 바꿉니다.

```powershell
scp -i "<PEM 경로>" .\docs\sql\exclude-two-youth-contests-h2.sql ubuntu@savannaexpedition.duckdns.org:/home/ubuntu/exclude-two-youth-contests-h2.sql
```

EC2 터미널에서 정상 종료와 백업을 진행합니다.

```bash
sudo systemctl stop savanna
if sudo systemctl is-active --quiet savanna
then
  echo "서비스가 실행 중입니다. DB 작업을 중단합니다."
  exit 1
fi

test -f /home/ubuntu/savanna-backend/data/savanna.mv.db || exit 1
umask 077
EXCLUSION_BACKUP_DIR="/home/ubuntu/savanna-backups/two-contests-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$EXCLUSION_BACKUP_DIR"
cp -- /home/ubuntu/savanna-backend/data/savanna.mv.db "$EXCLUSION_BACKUP_DIR/savanna.mv.db"
```

JAR 내부의 H2 Shell을 실행합니다. 비밀번호는 명령행에 넣지 않습니다. 이 실행은 Spring 앱을 시작하거나 스키마를 초기화하지 않습니다. 로컬의 메모리 H2로 해당 PropertiesLauncher 실행 방식을 검증했습니다.

```bash
java -Dloader.main=org.h2.tools.Shell \
  -cp /home/ubuntu/savanna-backend/build/libs/savanna-backend-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

URL 입력:

```text
jdbc:h2:file:/home/ubuntu/savanna-backend/data/savanna;IFEXISTS=TRUE
```

Driver는 org.h2.Driver, User/Password는 운영 config/application-secret.yml의 기존 DB 설정과 동일하게 입력합니다. Password는 공유하거나 로그로 출력하지 않습니다. H2가 다른 프로세스에 점유돼 있으면 강제 종료하지 말고 작업을 중단합니다.

먼저 SQL 프롬프트에서 두 대상 행을 확인합니다.

```sql
SELECT announcement_id, external_id, title, visibility, exclude_reason
FROM announcements
WHERE source_provider='CONTEST_KOREA'
  AND external_id IN ('202611040033','202610170003');
```

**두 행과 제목이 일치할 때만** 다음을 실행합니다. 0~1건이면 누락/다른 출처 여부를 확인하고 전체 제외 완료라고 보고하지 않습니다. 로컬 announcementId를 운영 DB에 사용하지 않습니다.

```sql
RUNSCRIPT FROM '/home/ubuntu/exclude-two-youth-contests-h2.sql' CHARSET 'UTF-8';
```

오류가 나면 `ROLLBACK;` 후 중단하고 원인을 확인합니다. 이어서 UPDATE/COMMIT를 수동으로 진행하지 않습니다. 성공 후 두 행의 visibility=false와 제외 사유, remaining_public=0을 확인하고 `exit`로 Shell을 종료합니다.

```bash
sudo systemctl start savanna
sudo systemctl is-active savanna
```

프런트에서 강제 새로고침/목록 재조회를 하고 Network의 GET /api/v1/announcements 검색 응답에 두 제목이 없는지 확인합니다. API에는 없는데 화면에 남으면 프런트 캐시·별도 데이터 소스를 점검해야 합니다. 브라우저에서 원본 ContestKorea API를 직접 조회하면 앱 DB 제외 정책이 적용되지 않습니다.

## 검증

대상 API·SQL 회귀 테스트 2개 통과. 실제 두 외부 ID의 참가 대상을 수집하면 목록 검색 0건·상세 404, 수동 비공개 후 재수집 유지, 정확한 source/ID만 수정, 기존 사유·행 보존 및 SQL 반복 실행 시 감사 이력 중복 방지를 메모리 DB로 확인했습니다. 운영 DB 적용과 실제 화면 검증은 남아 있습니다.
