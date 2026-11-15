# 사용자 지정 공고 삭제와 CSV 참가 대상 보정

## 삭제 대상

이번 요청은 비공개 전환이 아니라 announcements 레코드의 실제 삭제입니다. 제목 추정이나 청소년 키워드 전체 삭제를 사용하지 않고 CONTEST_KOREA와 아래 외부 ID를 함께 사용합니다.

| 외부 ID | 공고 |
|---|---|
| 202611020027 | 2026년 경상북도 도민 대상 규제개선 공모전 |
| 202610080029 | 2026 세계아동그림대회 |
| 202610140076 | 제3회 생성형 AI 경진대회 (초·중·고 학생문화경진대회) |
| 202610280043 | 제4회 산업기술 R&D 어린이 그림그리기 공모전 |
| 202610010030 | 제1회 중앙일보 어린이 미술대회 |
| 202610280023 | 2026 한·중·일 3국 초·중·고 포스터 공모전 |
| 202610010095 | 대한민국 국제 어린이 미술 대회 (Korea International Children's Art Competition) |
| 202610090004 | 2026 제 23회 고양호수만화축제 [청소년만화공모전] |
| 202611040033 | 어린이 미래 상상 그림 공모전 |
| 202610170003 | 2026 ACEL 전국 청소년 미술 공모전 |

경상북도 공모전 행만 ContestKorea.csv에서 제거했습니다(80→79행). 다른 9개는 현재 CSV에 없었습니다. 사용자 편집본의 나머지 모든 셀은 그대로 보존했습니다. 원본 target_raw에 남은 해당자 문구를 기준으로 다른 공고를 임의 삭제하지 않습니다.

## 보정 및 재유입 차단

- CSV 보정 열은 target_text이며 기존 구현은 target_raw만 반영하고 있었습니다. CSV 반영 시 target_text를 우선하고, 비어 있으면 기존 target_raw 동작을 유지하도록 수정했습니다.
- 수동 정리 명령은 기존 DB 행의 enrollment_requirement와 참가 대상 보정 출처 메타데이터만 갱신합니다. 날짜·분야·상세 정보·visibility·기존 제외 사유는 바꾸지 않습니다. 미매칭 CSV 행을 새로 생성하지 않습니다.
- 원본 target_raw와 사용자가 작성한 target_text를 provenance에 구분해서 보존합니다. API 재수집은 이미 보정된 참가 대상을 덮어쓰지 않습니다.
- ContestKoreaRemovalRegistry의 정확한 10개 ID를 API 및 CSV 수집 경로에서 저장 전에 차단합니다. 차단한 행은 suppressed로 별도 보고하며 실패 건수로 취급하지 않습니다. 다시 들어온 삭제 대상의 원문/이력도 DB에 새로 기록하지 않습니다.
- 정리 명령은 대상의 추천 캐시와 source event를 삭제한 뒤 공고를 삭제합니다. 원정 또는 알 수 없는 FK 참조가 있으면 APPLY 전체를 중단하며, 원정·제안·할 일을 연쇄 삭제하지 않습니다.

## 로컬 실행

앱을 정상 종료한 상태에서 실행합니다. 파일 H2 독점 잠금 확인과 백업 후, JPA 초기화나 외부 API 호출 없이 수행합니다. DB가 없으면 새로 만들지 않으며 앱 점유 시 강제 종료하지 않습니다.

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea '-PcontestKoreaCurate=PREVIEW' '-PcontestKoreaCsv=ContestKorea.csv' --no-daemon
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea '-PcontestKoreaCurate=APPLY' '-PcontestKoreaCsv=ContestKorea.csv' --no-daemon
```

PREVIEW는 ACCESS_MODE_DATA=r로 읽고 UPDATE/DELETE를 하지 않습니다. blockingReferences가 비어 있어야 APPLY할 수 있습니다. APPLY는 보정·이력·삭제를 단일 트랜잭션으로 수행하며 실패 시 롤백합니다.

## 로컬 실제 적용 결과 (2026-11-15)

- 삭제 전 공고 295건 → 삭제 후 285건.
- 지정한 외부 ID 10개에 해당하는 공고 10건 실제 삭제. 삭제 대상 잔존 0건을 별도 읽기 전용 JDBC 조회로 확인했습니다.
- 연결 원정·제안 없음. 사용자·팀·팀원·원정·제안·할 일·체크리스트·단계 이력의 행 수는 백업과 비교해 모두 동일했습니다.
- 삭제 공고의 source event 12건 제거, 추천 캐시 삭제 0건.
- CSV 79행 중 기존 DB 매칭 78건: 참가 대상 보정 71건, 이미 동일한 7건 유지. 다시 읽어 78건 모두 target_text와 DB 값 일치 확인.
- 미매칭 1건: 202610260010 / [제3회] 매경 AI 광고 공모전 대한민국 AI 광고대상. 이 요청에서 새로 생성하지 않았고 CSV에는 보존했습니다.
- 적용 후 PREVIEW: 삭제 후보 0건, 추가 보정 후보 0건, 잔존 0건.
- 적용 전 DB 백업: tmp/db-backups/savanna-before-contest-import-47605982-0eda-4953-9c27-041e72675fd0.mv.db.
- 사용자 CSV 편집본 백업: tmp/import-reports/contest-curation-csv-20261115/ContestKorea-before.csv.
- APPLY 보고서: tmp/import-reports/contest-korea-curation-4d5554d9-4bdf-471d-9108-bb2dad525868.json.
- 사후 PREVIEW 보고서: tmp/import-reports/contest-korea-curation-55b13e10-8c8d-4a12-99fd-b1b4082fc782.json.
- 전체 202개 테스트 통과(실패/오류 0), bootJar 성공. CSV 보정 우선순위/재수집 보존, 차단 ID 재유입 방지, 정확한 출처만 삭제, PREVIEW 무변경·재실행, FK 참조 시 중단, 실패 롤백을 검증했습니다.

## EC2 적용 순서 (직접 적용하지 않음)

새 JAR과 수정 CSV를 서버의 별도 경로(예: /home/ubuntu/savanna-new.jar, /home/ubuntu/ContestKorea.csv)에 올립니다. 운영 DB나 로컬 application-secret.yml을 업로드하지 않습니다. 새 JAR에는 재수집 차단 코드가 있어야 합니다. 기존 JAR로 계속 수집하면 삭제 후 다시 생성될 수 있습니다.

1. `sudo systemctl stop savanna`로 정상 종료하고 중지 상태를 확인합니다. 기존 JAR도 build 밖에 백업합니다.
2. 아래 PREVIEW를 새 JAR로 실행합니다. DB 백업은 명령이 자동 생성합니다. 운영 config/application-secret.yml의 기존 자격 증명을 읽으며 출력하지 않습니다.
3. deletionCandidates의 정확한 ID·제목과 blockingReferences를 확인합니다. 운영 DB에 원정 등 연결 데이터가 있으면 임의로 삭제하지 않고 중단합니다.
4. 동일 명령의 PREVIEW를 APPLY로 바꿔 실행합니다.
5. 잔존 0건과 보정/미매칭 결과를 확인하고 다시 PREVIEW로 재검증합니다.
6. 백업한 기존 JAR 위치를 기록한 후 새 JAR을 실제 서비스 JAR 경로에 교체하고 `sudo systemctl start savanna`로 시작합니다. 운영 환경 변수·시크릿은 유지합니다. 이전 배포의 미적용 마이그레이션 여부는 별도 확인합니다.

```bash
cd /home/ubuntu/savanna-backend
umask 077
java -Xmx256m \
  -Dloader.main=com.savanna.backend.integration.contestkorea.ContestKoreaImportCommand \
  -cp /home/ubuntu/savanna-new.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/home/ubuntu/savanna-backend/config/application-secret.yml \
  '--spring.datasource.url=jdbc:h2:file:/home/ubuntu/savanna-backend/data/savanna;IFEXISTS=TRUE' \
  --spring.jpa.hibernate.ddl-auto=none \
  --contest-korea.import.backup-directory=/home/ubuntu/savanna-backups/contest-curation \
  --contest-korea.import.curate-only=PREVIEW \
  --contest-korea.import.csv=/home/ubuntu/ContestKorea.csv
```

이 작업에서는 EC2에 접속·삭제·재배포하지 않았습니다. 따라서 배포 화면에서도 없어지려면 운영 DB 정리와 새 수집 코드 배포가 필요합니다. 이후 프런트 목록/추천 캐시를 새로 조회합니다. 단순 새 JAR 교체나 CSV 행 삭제만으로 기존 운영 DB 레코드가 자동 삭제되지는 않습니다.
