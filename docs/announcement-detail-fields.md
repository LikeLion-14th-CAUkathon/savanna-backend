# C03 화면 항목과 API 속성

2026-11-14 첨부 화면을 기준으로 기존 상세 응답에서 없었던 전용 속성은 **동의서 안내**입니다. consentRequirements를 추가했습니다. 지원 방법 문장(applicationInstructions), 양식(formNote)은 이미 상세 응답·CSV 저장에 있었지만 수동 생성 API 입력이 누락되어 이번에 연결했습니다.

| 화면 항목 | 속성/처리 | 현재 상태 |
|---|---|---|
| 접수 중·예정·마감 | receptionStatus | 기존 구현 |
| 공고명·주최·분야 | title / host / field | 기존 구현 |
| 원문 보기 | sourceUrl | 기존 구현 |
| 접수 시작·마감 날짜 | applicationStartsAt / deadline | 기존 구현, deadline은 날짜만 |
| 남은 일수·오늘·진행바 위치 | 날짜와 한국 기준 현재 날짜를 이용해 프런트 계산 | 별도 저장 속성 아님 |
| 참가 대상 | enrollmentRequirement | 기존 구현 |
| 인원 | minMembers / maxMembers | 기존 구현, null은 미확인 |
| 참가 가능 지역 | participationRegion | 기존 구현, 수동 생성 입력도 연결 |
| 개최 지역·현장 설명 | region / venueNote | 기존 구현, venueNote 수동 생성 입력도 연결 |
| 공모 주제 | theme | 기존 구현 |
| 심사기준 | evaluationCriteria | 기존 구현 |
| 지원 방법 설명 | applicationInstructions | 기존 상세 응답·CSV 유지, 수동 생성 입력 연결 |
| 접수 경로 분류 | applicationMethod (WEB/EMAIL/OTHER) | 기존 구현; 설명 문장과 구분 |
| 제출물 | submissionRequirements | 기존 구현 |
| 양식 | formNote | 기존 상세 응답·CSV 유지, 수동 생성 입력 연결 |
| 분량 등 제한 | lengthNote | 기존 상세 응답·CSV 유지, 수동 생성 입력 연결 |
| 동의서 | consentRequirements | 새 DB 컬럼·생성 요청·상세 응답·CSV 보완 |

실제 마감 시각은 이전 결정대로 제공하지 않습니다. 화면의 18:00/23:59를 실제 공고에서 받은 값인 것처럼 표시하지 않습니다. 고정 안내 문구와 참여 제안 버튼은 화면 구성 요소이며 제안 전송 API는 기존 POST /api/v1/announcements/{announcementId}/proposals를 유지합니다.

## 생성 요청

`POST /api/v1/announcements`에 아래 선택 속성을 추가했습니다. 기존 요청은 그대로 유효합니다. 생성 응답은 기존 요약 DTO이고 상세 내용은 GET /api/v1/announcements/{announcementId}에서 조회합니다.

```json
{
  "title": "설명용 테스트 공고",
  "host": "테스트 주최기관",
  "field": "IDEA_ARCHITECTURE_STARTUP",
  "deadline": "2026-12-31",
  "applicationInstructions": "주최 측 접수 페이지에서 온라인 제출",
  "submissionRequirements": "아이디어 기획서 PDF, 참가 신청서",
  "formNote": "주최 측 지정 양식 사용",
  "lengthNote": "10쪽 이내",
  "consentRequirements": "개인정보 수집·이용 동의서 · 팀원 전원 서명",
  "participationRegion": "전국",
  "region": "서울",
  "venueNote": "온라인 · 서울 현장 발표"
}
```

위 문구는 계약 설명용 예시이며 실제 공고에 자동 저장하지 않습니다. 신규 설명 필드는 최대 10,000자, participationRegion은 최대 255자입니다. 생략/null/공백은 상세값을 만들지 않습니다. '원문 참고' 같은 정확한 자리표시 문구도 새 보완 필드에는 저장하지 않습니다.

## 동의서 필드 의미

consentRequirements는 **공고가 요구하는 동의서 종류·서명·제출 안내문**입니다. 사용자가 실제 동의했는지 나타내는 boolean이 아니며, 동의서를 업로드·전자서명하는 기능도 아닙니다.

상세 응답은 항상 consentRequirements 키를 제공하며 미확인이면 null입니다. null은 제출 불필요를 뜻하지 않습니다. 프런트 타입 예시는 `consentRequirements: string | null`이며 배포 전후 호환을 위해 접근 시 optional chaining으로 null/undefined를 모두 처리할 수 있습니다.

지원 방법·제출물 패널은 applicationInstructions, submissionRequirements, formNote, consentRequirements 등 표시 가능한 값이 하나라도 있으면 보여줍니다. 동의서만 값이 있는 경우에도 패널이 사라지지 않도록 새 속성을 조건에 포함해야 합니다. 모든 표시값이 없으면 패널을 숨기고 원문 보기 버튼은 유지합니다.

## 수집·기존 공고 보완

ContestKorea 기본 API는 동의서 정보를 제공하지 않아 자동으로 만들어 채우지 않습니다. 검증된 원문 정보가 있을 때 CSV의 선택 컬럼 `consent_requirements`로 기존 외부 공고를 api_id 기준 보완할 수 있습니다. 기존 CSV는 이 컬럼이 없어도 그대로 읽힙니다. 원본 CSV는 변경하지 않았습니다.

빈 셀·해당 컬럼 누락·API 재수집은 저장된 동의서 안내를 지우지 않습니다. 원본 행과 출처는 기존 CSV 감사 이벤트/메타데이터로 보존합니다. 개인정보 동의서를 모든 공고의 공통 기본값으로 넣지 않습니다.

## 배포와 DB

새 nullable TEXT 컬럼은 `announcements.consent_requirements`입니다. H2 명시적 SQL은 [동의서 컬럼 SQL](sql/announcement-consent-requirements-h2.sql)에 있습니다. 운영 적용 시 서비스 정상 중지 → 운영 DB 백업 → 컬럼 추가 → 새 JAR 배포 → 재시작 → Swagger와 실제 상세 응답의 새 키 확인 순서로 진행합니다. SQL은 기존 행을 삭제·초기화·가짜 값으로 채우지 않으며 반복 실행할 수 있습니다. H2 외 DB는 해당 DB의 문법으로 별도 적용해야 합니다.

이번 작업은 코드와 격리된 테스트 DB 검증이며 실제 로컬 파일 DB의 컬럼 추가와 EC2 배포/DB 변경은 실행하지 않았습니다. 기존 데이터는 컬럼 추가 후 null이며, 속성을 구현한 것과 실제 원문 데이터를 보완하는 것은 별개입니다.
