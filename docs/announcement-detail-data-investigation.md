# 2026-11-14 공고 상세 정보 표시 확인

실제 첨부 화면의 공고는 **2026 제18회 전국청소년 모형항공기대회**입니다. 로컬 announcementId=199, ContestKorea externalId=202610280017로 확인했습니다. 로컬 DB에는 읽기 전용(IFEXISTS=TRUE;ACCESS_MODE_DATA=r)으로 접속했습니다.

| 필드 | 로컬 저장값 |
|---|---|
| enrollmentRequirement | 초등학생, 중학생, 고등학생 |
| participationRegion | 경 남 |
| minMembers / maxMembers | null / null |
| region | null |
| theme / evaluationCriteria | null / null |
| applicationMethod / applicationInstructions | null / null |
| submissionRequirements / formNote / lengthNote | null / null / null |
| csvMetadata | 없음 |
| dateSource / deadline | API_ESTIMATED / 2026-11-14 |
| applicationPeriodRaw | 10.26~11.14 |
| excludeReason | [YOUTH_ONLY] 초·중·고생/청소년 전용 공고 |

해당 ID/제목은 첨부 CSV에 없습니다. 외부 API에는 참가 대상과 참가 가능 지역 등 기본 정보만 있고 인원·주제·심사기준·제출물·지원 방법은 제공하지 않습니다. 따라서 보완되지 않은 공고의 상세값을 null로 반환하는 것이 현재 계약이며, 프런트의 null 행 숨김 결과가 첨부 화면과 일치합니다. 이 자료만으로 프런트가 값이 있는 필드를 누락했다고 판단할 근거는 없습니다.

전체 CSV 80행도 완성된 상세 80건을 의미하지 않습니다. detail_status=filled 26행, api_only 54행이며 실제 값은 주제 26행, 심사기준 8행, 인원 최소·최대 각 12행, 제출물 18행, 지원 방법 25행, 양식 메모 13행뿐입니다. CSV 반영은 없는 내용을 생성하지 않습니다.

## 배포 확인과 API 영향

배포 서버 공개 /v3/api-docs를 읽어 새 enum 세 값과 applicationInstructions/formNote/lengthNote/venueNote/detailStatus/dateSource 등 상세 보완 응답 필드가 이미 스키마에 있음을 확인했습니다. 따라서 스키마가 예전 버전이라는 가정만으로 현재 현상을 설명하지 않습니다. 인증이 필요한 운영 상세 API/DB 값은 직접 조회하지 않았으므로 실제 EC2 데이터와의 값 대조는 프런트 Network 응답이나 운영 DB 점검이 별도로 필요합니다. 운영 토큰을 공유할 필요는 없습니다.

이번 청소년 정책에서 경로·메서드·요청/응답 필드와 enum을 추가 변경하지 않았습니다. 아래 API의 노출 동작이 달라집니다.

- GET /api/v1/announcements: 청소년 전용으로 제외한 공고가 목록에서 빠집니다.
- GET /api/v1/announcements/{announcementId}: 제외 공고는 404입니다.
- GET /api/v1/teams/{teamId}/recommendations, POST .../recommendations/refresh: 제외 공고가 저장 추천 조회·새 추천 후보에서 빠집니다.
- POST /api/v1/announcements: 수동 생성 공고에도 참가 대상 정책을 적용합니다.
- 외부 수집과 CSV 보완: 동일 정책 및 기존 제외 사유 보존을 적용합니다.

공고 분야 enum은 이미 확정한 ACADEMIC_SCIENCE_IT / IDEA_ARCHITECTURE_STARTUP / ART_DESIGN_WEBTOON을 그대로 사용합니다. 프런트 common.ts의 이전 네 값을 교체해야 합니다.

새 JAR 배포만으로 기존 운영 데이터의 excludeReason이 자동 추가되는 것은 아닙니다. EC2의 기존 공고에도 정책을 적용하려면 운영 DB 백업·서비스 정상 중지 후 [청소년 정책 문서](announcement-audience-policy.md)의 PREVIEW → APPLY를 별도 실행하고 서비스를 시작합니다. 명령은 프로젝트 루트/기존 DB 상대 경로/운영 설정을 유지해야 하며 H2만 지원합니다. 이번 작업에서는 EC2 코드 배포·DB 변경을 실행하지 않았습니다.

운영 CSV 보완이 아직 반영되지 않았다면 별도 데이터 반영이 필요하지만, 이 공고는 CSV에 없어서 CSV를 반영해도 상세가 새로 생기지 않습니다. 또한 이 공고는 청소년 전용 제외 대상이므로 현재 기준에서는 노출에서 빠지는 것이 맞습니다.

## 프런트 필드 연결

| 화면 항목 | 상세 응답 필드 |
|---|---|
| 참가 대상 | enrollmentRequirement |
| 인원 | minMembers / maxMembers |
| 참가 가능 지역 | participationRegion |
| 개최·현장 지역 / 설명 | region / venueNote |
| 공모 주제 | theme |
| 심사기준 | evaluationCriteria |
| 지원 방법 문장 | applicationInstructions (applicationMethod enum과 별도) |
| 제출물 | submissionRequirements |
| 양식 | formNote |
| 동의서 제출 안내 | consentRequirements (2026-11-14 추가) |
| 분량·제한 메모 | lengthNote |

초기 조사 시 동의서는 독립 응답 필드가 없었습니다. 후속 구현으로 consentRequirements를 추가하고 생성 요청·DB 저장·상세 응답·선택 CSV 컬럼(consent_requirements)을 연결했습니다. [전체 화면 항목 매핑과 계약](announcement-detail-fields.md)을 참고하세요. 실제 동의서 안내가 없으면 null이며 예시 문구를 실제 공고에 저장하지 않습니다. 누락값은 숨기고 원문 링크는 유지합니다.

첨부 화면의 23:59는 백엔드에서 반환한 실제 마감 시각이 아닙니다. API는 deadline=YYYY-MM-DD만 반환하므로 화면에 공고가 제공한 실제 시각인 것처럼 표시하지 않아야 합니다. 목록 응답은 상세 필드가 없는 요약 DTO이므로 상세 화면에서는 반드시 GET /api/v1/announcements/{announcementId} 응답을 사용해야 합니다.
