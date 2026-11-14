# 우리 팀 공고 추천 (C01)

모든 요청에 기존 JWT의 `Authorization: Bearer <accessToken>`이 필요합니다. 해당 팀의 실제 팀원만 조회·재생성할 수 있습니다.

## 추천 목록 조회

`GET /api/v1/teams/{teamId}/recommendations`

```json
[
  {
    "announcementId": 10,
    "title": "청년 생활 아이디어 공모전",
    "host": "주최기관",
    "field": "IDEA_ARCHITECTURE_STARTUP",
    "deadline": "2026-11-20",
    "status": "OPEN",
    "score": 85,
    "reason": "수면 습관 코칭 프로젝트를 청년 생활 문제 해결 아이디어로 연결할 수 있어요.",
    "eligibility": {
      "announcementId": 10,
      "teamId": 1,
      "status": "TEAM_INFO_REQUIRED",
      "conditions": [
        {"code":"ADDITIONAL_REQUIREMENTS", "status":"TEAM_INFO_REQUIRED", "message":"참가 대상에 별도 조건이 있습니다. 공고 상세 원문을 확인해야 합니다."}
      ]
    },
    "generatedAt": "2026-11-11T10:00:00",
    "source": "AI",
    "stale": false
  }
]
```

예시의 reason은 API 형식 설명용입니다. 실제 제공자 설정은 OpenAiRecommendationAiClient 및 recommendation.ai 설정을 사용합니다.

- 저장된 추천을 score 내림차순, 동점이면 마감일·공고 ID 오름차순으로 반환합니다.
- 점수는 0~100의 적합도이며 합격 확률이 아닙니다.
- GET은 AI를 호출하거나 데이터를 변경하지 않습니다. 추천이 없으면 `[]`입니다.
- 조회 시점에도 비공개·마감·접수 예정·접수 시작 미확인 공고와 명확한 참가 조건 불일치를 제외합니다. 한국 날짜 기준 마감일 당일은 포함합니다.
- 팀·팀원 정보 또는 추천에 사용한 공고 내용이 변경되면 stale=true입니다. 이후 재생성 전까지 캐시된 추천 이유를 반환합니다.
- eligibility는 현재 팀 정보로 다시 계산한 결과입니다. 해당자·지역 자격 등 확인 필요 상태를 조건 충족으로 바꾸지 않습니다.

## 추천 재생성

`POST /api/v1/teams/{teamId}/recommendations/refresh`

요청 본문은 없고, 성공 응답은 GET과 같은 목록입니다.

1. 팀 소속을 확인하고 프로젝트 이름·소개·기술 스택과 팀원 재학 상태를 읽습니다.
2. 공개·접수 중·마감 전 공고를 마감순으로 최대 200개 조회합니다.
3. 기존 참가 조건 비교에서 MISMATCH를 제외하고 최대 20개를 AI 후보로 전달합니다. TEAM_INFO_REQUIRED는 확인 필요 정보와 함께 포함합니다.
4. AI 호출은 DB 트랜잭션 밖에서 수행합니다. 사용자 이름·이메일·인증 토큰은 AI 입력에 포함하지 않습니다.
5. 후보 ID 포함 여부, ID 중복, 0~100 점수, 1~500자 추천 이유를 검증합니다.
6. 점수순 상위 5개를 저장합니다. 저장 전에 팀 소속·팀 입력 변경·선택 공고 변경을 재확인하고 해당 팀의 캐시만 같은 트랜잭션으로 교체합니다.

- 후보가 없으면 AI를 호출하지 않고 해당 팀의 추천을 빈 목록으로 교체합니다.
- 유효한 AI 응답이 빈 목록이어도 추천 없음으로 저장합니다.
- AI 호출·결과 검증 실패 시 기존 추천을 삭제하지 않습니다.
- 동시에 여러 재생성이 완료되면 팀 행 잠금으로 저장을 직렬 처리합니다. 중복 행 없이 마지막 정상 교체 결과가 남습니다.
- 팀 정보 변경 자체에서 자동 AI 호출은 하지 않습니다. 화면에서는 GET이 []이거나 stale=true이면 POST를 호출하는 흐름을 사용합니다. POST를 직접 호출하면 현재 정보로 다시 생성합니다.
- field는 ACADEMIC_SCIENCE_IT / IDEA_ARCHITECTURE_STARTUP / ART_DESIGN_WEBTOON입니다. CSV 제외 사유 또는 분류 보류가 있는 공고는 후보와 저장 추천 조회 모두에서 제외합니다.

## 오류

| HTTP | 의미 |
|---|---|
| 400 | 잘못된 팀 ID |
| 401 | 미인증 |
| 403 | 다른 팀에 접근 |
| 404 | 팀 없음 |
| 409 | AI 호출 중 팀·팀원 또는 선택 공고 정보가 바뀜. 현재 정보로 재시도 필요 |
| 502 | RECOMMENDATION_AI_INVALID_RESPONSE: 허용되지 않는 ID·중복·점수·추천 이유 등 잘못된 AI 응답 |
| 503 | RECOMMENDATION_AI_UNAVAILABLE: AI 제공자 미연결 또는 호출 실패 |

후보가 존재하지만 AI 제공자가 없으면 503이며, 임의의 추천을 실제 AI 결과처럼 저장하지 않습니다.

## API 키 없는 개발·테스트

테스트용 추천을 화면에서 확인하려면 앱 시작 시 두 프로필을 명시합니다.

```powershell
.\gradlew.bat --gradle-user-home "$env:USERPROFILE\.gradle" bootRun --args="--spring.profiles.active=local,recommendation-mock"
```

- local 또는 test와 recommendation-mock을 함께 지정해야 가짜 생성기가 활성화됩니다.
- prod 또는 production이 있으면 가짜 생성기는 활성화되지 않습니다.
- 가짜 응답은 source=MOCK이며 추천 이유에도 [테스트용 추천]을 표시합니다. 실제 적합도 평가가 아닙니다.
- 기본 실행에는 가짜 생성기가 등록되지 않습니다.
- 실제 AI 구현은 OpenAiRecommendationAiClient이며 recommendation.ai.enabled와 키·모델 설정으로 활성화합니다. 연결/응답 시간 제한, 없는 정보 추측 금지 지침 및 구조화 응답 파싱을 사용합니다.
- 입력은 프로젝트 정보와 후보 공고·조건에 한정합니다. 상단 예시처럼 공고와 프로젝트의 연결점을 짧게 설명하는 이유를 생성하도록 합니다.

저장 테이블은 announcement_recommendations이며 팀·공고 조합은 유일합니다. 공고 수집 명령은 AI 추천을 재생성하지 않습니다. 수집 후 변경된 후보로 갱신하려면 refresh API를 호출합니다.
