# 분야 변경·ALL 수집·CSV 보완

프런트에 전달할 분야 값은 아래 세 개뿐입니다. 목록 field 필터, 생성 요청, 상세·목록·추천 응답 및 Swagger에 동일하게 적용됩니다. 이전 enum은 요청에 사용할 수 없습니다.

| 외부 옵션 / 코드 | 새 enum | 표시 이름 |
|---|---|---|
| IT / 030310001 | ACADEMIC_SCIENCE_IT | 학문·과학·IT |
| IDEA / 031410001 | IDEA_ARCHITECTURE_STARTUP | 아이디어·건축·창업 |
| DESIGN / 030610001 | ART_DESIGN_WEBTOON | 미술·디자인·웹툰 |

## 실행 순서

1. 로컬 앱을 정상 종료합니다. 명령은 파일 H2 독점 잠금과 백업을 확인하고 시작합니다. 점유 중이면 실패하며 앱을 강제 종료하지 않습니다. EC2에 접속하지 않습니다.
2. 프로젝트 루트에서 다음을 실행합니다. 백업 → JPA 이전 enum 마이그레이션 → 스키마 확장 → 세 분야 API 조회·저장 → CSV 보완 → 실제 목록/분야 필터 HTTP 검증 순서입니다.

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea -PcontestKoreaCategory=ALL -PcontestKoreaMigrate=true "-PcontestKoreaCsv=ContestKorea.csv" --no-daemon
```

3. 재수집은 migrate 옵션을 생략합니다. 신규가 0인지와 외부 출처+ID 유일성을 확인합니다. CSV를 다시 적용하지 않을 때는 Csv 옵션도 생략할 수 있습니다.

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea -PcontestKoreaCategory=ALL "-PcontestKoreaCsv=ContestKorea.csv" --no-daemon
```

개별 IDEA/IT/DESIGN 명령도 그대로 지원합니다. 기본은 IDEA입니다. 일반 앱 실행은 수집·CSV 적용을 수행하지 않습니다. 백업은 tmp/db-backups, 실행 보고서는 tmp/import-reports에 저장됩니다. 설정 파일의 키·비밀번호는 인자로 전달하지 않습니다. 로컬 파일/메모리 H2 이외의 접속 URL은 이 명령에서 거절합니다.

## 외부 API의 수집 범위

제공 PDF 2쪽은 데모 최신 10건과 limitsu로 반환 수를 조정하는 예시(limitsu=100)를 제공합니다. 고정 최대 허용 수, 페이지/offset, 전체 건수, 커서 및 과거 전체 이력 조회는 명시하지 않습니다. 세 분야 각각 limitsu=100으로 한 번 조회합니다. 100을 서버의 확정 최대치라고 해석하지 않습니다. ALL은 세 분야 모두 요청한다는 뜻이며 전체 기간 전수 수집을 뜻하지 않습니다.

보고서 datasetComplete=false, coverage=LATEST_BATCH_ONLY를 유지합니다. 받은 수가 100보다 적어도 전체 데이터가 모두 내려왔다는 근거로 사용하지 않습니다. 누락된 공고를 삭제·자동 마감하지 않습니다.

## 중복·분야 근거

sourceProvider=CONTEST_KOREA + externalId가 유일합니다. 같은 공고의 관측 분야를 externalCategoryCodes에 정렬하여 누적하고 MULTIPLE_CATEGORIES로 보고합니다. STR_MENU와 CSV category_source를 대표 분야 근거(primaryCategoryCodes)로 사용합니다. 대표 근거가 하나면 관측 요청 순서와 관계없이 같은 분야입니다. 대표 근거가 없으면 관측 분야가 하나일 때만 확정합니다. 서로 다른 대표 근거가 있으면 field=null, classificationIssue로 공개·추천에서 보류합니다. 마지막 요청이 앞선 분야를 덮어쓰지 않습니다.

이전 DEVELOPMENT_HACKATHON/IDEA_PLANNING/DESIGN은 대응하는 새 enum으로 이전합니다. SOCIAL_ESG는 STR_MENU, 알려진 원문 링크의 Txt_bcode, CSV category_source 근거를 확인합니다. 근거가 없거나 충돌하면 임의 분류하지 않고 이전 값을 감사 테이블에 보존하고 분류 보류합니다. 마이그레이션을 먼저 실행해야 이전 enum 때문에 JPA 역직렬화가 실패하지 않습니다.

## CSV 컬럼과 출처

원본 CSV는 수정하지 않습니다. UTF-8(BOM 포함), 인용 부호·쉼표·여러 줄 셀을 읽습니다. api_id로 연결하고 category는 사용하지 않습니다. category_source를 정확한 외부 분야 이름으로 매핑합니다. 없는 공고는 제목·주최·명시적 ISO 날짜·원문 URL과 양의 인원값 등 필수값 검증을 통과할 때 생성합니다. 알 수 없는 분야는 생성하지 않고 UNMATCHED 감사 이벤트에 전체 행을 보존합니다. 기존 공고라면 분류 보류합니다.

| CSV | 저장/사용 |
|---|---|
| topic / criteria / deliverables | theme / evaluationCriteria / submissionRequirements |
| how_to_apply / form_note / length_note | applicationInstructions / formNote / lengthNote (문자열 원문) |
| team_min / team_max | minMembers / maxMembers, 빈 값은 null·기존 값 유지 |
| target_raw | enrollmentRequirement |
| allowed_status | 원문 보존; any/enrolled/enrolled\|on_leave만 재학 조건 비교 |
| team_type | teamType 원문, team_unknown을 무제한으로 변환하지 않음 |
| required_region | csvRequiredRegionRaw, 참가 거주지/개최지 여부 불명확하여 자격 자동 판정에 쓰지 않음 |
| venue_note / detail_status | 원문 보존·상세 응답 제공 |
| apply_start / apply_end | 명시된 연도를 그대로 저장, 날짜 출처 CSV |
| exclude_reason | 사유 보존, 비공개·추천 제외; 다음 빈 셀/재수집으로 해제하지 않음 |
| status / category | 원본 메타데이터에만 보존; 현재 상태는 날짜로 계산, category로 분야 결정하지 않음 |

CSV 전체 행·파일명·SHA-256·행 번호와 날짜 충돌의 이전/선택 값은 csvMetadata 및 announcement_source_events에 기록합니다. API 날짜는 apiStartDate/apiDeadline, CSV 날짜는 csvStartDate/csvDeadline에 따로 보존합니다. API의 2026 시작/연말 이월 2027 규칙은 이전에 정한 추정 정책이며 dateSource=API_ESTIMATED입니다. CSV 연도는 추측하지 않습니다. 날짜가 충돌하면 명시된 CSV를 적용하고 차이를 보고합니다. API 재수집은 CSV·수동 상세를 지우지 않습니다.

첨부 자료의 확인 사항:

- 80행 중 IT 22, IDEA 33, DESIGN 22 = 지원 77행.
- 사진·영상·영화제 3행: 202610120005, 202611020003, 202610260010. 새 세 분야에 해당하지 않으므로 미매칭.
- 제외 사유 11행. 제외 설명과 명시된 날짜가 충돌하는 202611100010, 202609100010은 날짜를 임의 수정하지 않고 제외 상태를 유지. 특히 후자의 2027년 날짜를 2026년으로 바꾸지 않음.
- required_region 202611070021(서울), 202611020001(충남 천안), 202611050035(경기 성남)은 현장 지역과 겹쳐 의미 확인 필요.
- detail_status의 api_only/filled는 보완 자료의 표시이며 원문 검증 완료를 뜻하지 않음. 공고 원문을 추가로 크롤링하거나 CSV의 정확성을 검증했다고 주장하지 않음.
- 실제 DESIGN API 응답에도 네이밍·슬로건, 문학·문예, 사진·영상·영화제 등 요청 분야와 다른 대표 분야가 섞여 있었습니다. 지원하는 세 분야로 억지 매핑하지 않고 UNSUPPORTED_API_CATEGORY로 보류합니다. 이력과 최신 원문은 DB에 보존합니다.

## 누락 정보와 프런트 표시

없는 상세값은 null을 반환합니다. DB에 "원문 참고"를 저장하지 않습니다. 기존 해당 문구도 마이그레이션에서 null로 정리합니다. 프런트는 null이면 **공고 원문을 확인해 주세요**를 표시하고 sourceUrl로 연결하세요. minMembers/maxMembers null은 **참가 인원 확인 필요**이며 0명·인원 무제한이 아닙니다. 부분적으로 알려진 범위만 판단합니다.

프런트 저장소는 이 작업 공간에 없으므로 프런트 코드는 변경하지 않았습니다. 상세 DTO에 보완 필드를 제공하며 applicationMethod enum과 CSV의 자유문장 applicationInstructions를 구분합니다. CSV의 원시 지역은 검증된 참가 가능 지역 participationRegion과 구분하세요. AI 입력에는 누락값을 그대로 전달하며 프롬프트는 없는 주제·제출물·자격을 추측하지 않도록 이미 제한합니다. 제외/분류 보류 데이터는 저장된 추천 조회와 새 후보 모두에서 제거합니다.

## 배포 (이번 작업에서 EC2 실행 금지)

배포 DB가 H2인 경우에만: 서비스 정상 중지 → DB 파일 외부 보관 백업 및 복구 경로 확보 → 새 코드/CSV/기존 secret 설정 배치 → 위 명령과 동일한 Gradle 작업(리눅스는 ./gradlew, 사용자 홈 Gradle 옵션은 환경에 맞춤)으로 migrate=true+ALL+CSV 실행 → 보고서 확인 → migrate 없이 재수집 신규 0/중복 0 확인 → 서비스 시작 순서입니다. 새 enum 앱을 기존 DB에 먼저 기동하지 마세요. 이 명령은 JPA 기동 전에 마이그레이션합니다.

프로젝트 루트와 기존 DB 상대 경로를 유지한 Linux 실행 예시(이번 작업에서는 실행하지 않음):

```bash
./gradlew importContestKorea -PcontestKoreaCategory=ALL -PcontestKoreaMigrate=true -PcontestKoreaCsv=ContestKorea.csv --no-daemon
./gradlew importContestKorea -PcontestKoreaCategory=ALL -PcontestKoreaCsv=ContestKorea.csv --no-daemon
```

SQL 참고 파일은 docs/sql/announcement-fields-v2-h2.sql입니다. 실행 중인 앱이 없는 백업된 H2에서만 적용합니다. JDBC 마이그레이터는 제약 이름을 자동 조회하고 CSV 근거도 확인하므로 권장 경로입니다. SQL을 직접 적용한다면 이전 CHECK 제약 이름과 CSV 근거를 먼저 확인해야 합니다. MySQL/PostgreSQL 배포라면 H2 SQL을 실행하지 말고 실제 DB의 enum/check DDL에 맞는 별도 마이그레이션이 필요합니다.

실패/미매칭이 있는 보고서를 성공 전수 수집으로 처리하지 마세요. 각 분야 received/created/updated/excluded/failedItems와 CSV created/updated/excluded/unmatched/failed/dateConflicts를 구분합니다. 분야별 제외 수는 API 저장 시점이며 이후 CSV 제외를 포함한 최종 집계는 별도 확인합니다.
