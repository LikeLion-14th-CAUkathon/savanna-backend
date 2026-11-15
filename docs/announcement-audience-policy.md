# 청소년 전용 공고 제외

확정 범위는 **초·중·고생/청소년 전용 공고만 제외**입니다. 청년 공고, 대학생·일반인도 참가 가능한 공고, 누구나 참가 가능한 공고는 이 정책으로 제외하지 않습니다. API 분야 enum은 변경하지 않습니다.

참가 대상 원문(enrollmentRequirement / API STR_TARGET / CSV target_raw)을 기준으로 판단합니다. 쉼표·가운뎃점·및·또는 등으로 구분된 명확한 대상 목록이 유치원·초등학생·중학생·고등학생·청소년 등으로만 구성될 때 자동 제외합니다. 대회 제목에 청소년이 있다는 이유로는 제외하지 않습니다.

| 원문 예 | 처리 |
|---|---|
| 초등학생, 중학생, 고등학생 | 제외 |
| 청소년 | 제외 |
| 고등학생, 대학생 | 유지 |
| 누구나, 초등학생, 중학생, 고등학생, 대학생, 일반인 | 유지 |
| 청년 | 유지 |
| 청소년, 해당자 ▶ | 검토 필요, 자동 제외하지 않음 |
| 제목은 청소년 관련이나 대상 없음 | 검토 필요, 자동 제외하지 않음 |
| 제목은 청소년 관련이나 대학생·일반인도 포함 | 대상 혼합/원문 검토 대상으로 보고, 자동 제외하지 않음 |

나이 범위를 법적 청소년 정의로 추정하거나 복잡한 부정 문장을 해석해서 분류하지 않습니다. `대학생 제외`라는 문구를 대학생 포함으로 오해하지 않으며 모호한 대상은 검토 목록에 남깁니다.

## 저장·공개·재수집

- API 수집, CSV 보완, 수동 공고 생성에 적용합니다.
- 제외는 원본 삭제가 아닙니다. excludeReason에 `[YOUTH_ONLY] 초·중·고생/청소년 전용 공고`를 남깁니다.
- 기존 공개 목록·상세·새 추천 후보·저장 추천 조회는 excludeReason이 있는 공고를 제외합니다. 원정 등 기존 관계 데이터는 삭제하지 않습니다.
- 기존 CSV·수동 제외 사유와 수동 visibility 설정은 보존합니다. 정책 때문에 수동 visibility를 덮어쓰지 않습니다.
- 대상이 명확히 대학생·일반인·누구나 등으로 보완되면 이 정책의 사유만 제거합니다. 다른 제외 사유/분류 보류/수동 비공개가 있으면 계속 숨깁니다. 대상이 단순히 사라지거나 모호해졌다는 이유로 자동 해제하지 않습니다.
- 정책 적용·해제는 announcement_source_events의 AUDIENCE_EXCLUSION / AUDIENCE_REASSESSED에 근거를 보존합니다. 반복 수집으로 같은 사유/감사 이벤트를 중복 추가하지 않습니다.

## 기존 로컬 데이터 점검과 적용

로컬 앱을 정상 종료하고 프로젝트 루트에서 실행합니다. 기존 importContestKorea 명령에 audience-only 모드를 추가했으며, 이 모드에서는 외부 API 호출·CSV 반영·JPA 스키마 변경·검증용 사용자 생성 없이 **기존 DB의 대상 정보만** 점검합니다.

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea -PcontestKoreaAudience=PREVIEW --no-daemon
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" importContestKorea -PcontestKoreaAudience=APPLY --no-daemon
```

두 모드 모두 파일 H2는 먼저 독점 잠금 확인과 백업을 수행합니다. PREVIEW는 ACCESS_MODE_DATA=r로 읽기 전용 연결하고 데이터 UPDATE를 하지 않습니다. APPLY는 기존 데이터의 해당 정책 사유와 감사 이력을 하나의 트랜잭션으로 저장하며 감사 저장 실패 시 롤백합니다. 앱이 DB를 점유하면 중단하고 강제 종료하지 않습니다. 파일이 없는 경우 새 DB를 생성하지 않습니다.

보고서는 tmp/import-reports/announcement-audience-*.json에 생성됩니다. scanned는 점검 수, exclusionCandidates는 명확한 청소년 전용 대상 수(기존 제외 포함), changed는 실제 변경 수, released는 이 정책의 사유 해제 수입니다. kept에는 변경하지 않는 검토 대상도 포함되며 review 목록을 별도로 확인합니다. 재적용 시 조건이 같으면 changed=0입니다.

첨부 CSV 80행에서는 50행의 참가 대상에 청소년층이 언급되지만 대학생·일반인 등이 함께 포함되어 있습니다. 단순히 '초등학생/중학생/고등학생'이라는 단어를 포함한다고 모두 제외하지 않습니다. CSV 원본은 변경하지 않습니다.

EC2는 이번 작업에서 변경하지 않습니다. 배포 DB에 적용하려면 운영 백업·앱 정상 중지 후 같은 버전의 정책 명령을 별도로 실행해야 합니다. 명령은 파일/메모리 H2만 지원하며 다른 DB에 그대로 사용하지 않습니다.

## 프런트 common.ts와의 관계

`src/api/common.ts`는 프런트 저장소에서 API 공통 타입을 선언하는 TypeScript 파일입니다. 이 백엔드 저장소에는 해당 파일이 없습니다. AnnouncementField는 ACADEMIC_SCIENCE_IT / IDEA_ARCHITECTURE_STARTUP / ART_DESIGN_WEBTOON 세 값이며, 청소년 대상 제외는 분야가 아니라 **노출 정책**이므로 enum을 추가하거나 이전 분야로 되돌리지 않습니다. 프런트에서 제목으로 별도 필터링할 필요가 없습니다.

## 2026-11-14 로컬 적용 결과

- 공고 295건 점검, 명확한 청소년 전용 29건에 정책 제외 사유 적용, 검토 대상 20건은 자동 제외하지 않음.
- PREVIEW 전후 DB SHA-256 동일: F332C5410BC6618117AAE0D682F6055560FAF318A045B4860EFB9B2A073D5C54.
- APPLY 전 백업: tmp/db-backups/savanna-before-contest-import-c7dee1fb-162f-44b2-ac71-cada06cf34af.mv.db.
- 최초 적용 보고서: tmp/import-reports/announcement-audience-9b9eb1e1-05b1-47e7-bad4-f53ef3f6895b.json (changed=29).
- 재적용 보고서: tmp/import-reports/announcement-audience-a93618ca-5c02-4928-be6c-fd505669dc9d.json (changed=0).
- 전체 테스트 145건 통과, 실패/오류 0. 원본 CSV와 공고·원정 관계 데이터를 삭제하지 않음.
- 검토에는 대학생 등이 함께 나열된 청소년 제목 공고와 `초등학생, 해당자 ▶` 같은 모호한 대상이 포함됨. 원문 확인 없이 추가 제외하지 않음.
- EC2 배포 및 운영 DB 적용은 실행하지 않음.
