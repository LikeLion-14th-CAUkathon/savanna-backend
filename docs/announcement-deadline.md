# 공고 마감일

deadline은 LocalDate / SQL DATE입니다. 생성 요청과 공고 목록·상세·원정 응답 모두 YYYY-MM-DD 형식을 사용합니다.

```json
{"title":"공고", "host":"주최기관", "field":"IDEA_PLANNING", "deadline":"2026-12-20"}
```

- 한국 날짜 기준으로 마감일 당일까지 유효하고 다음 날부터 CLOSED입니다.
- 기본 공고 목록과 OPEN·UPCOMING 필터도 마감일 당일을 포함합니다.
- 접수 시작일과 마감일은 같을 수 있습니다. 마감일이 시작일보다 빠르면 400입니다.
- 참여 제안은 기존 접수 시작 검증을 유지하며 마감일 당일까지 가능합니다. 시작값 미설정·접수 시작 전에는 기존처럼 거절합니다.
- 원정 목표일은 마감일과 같아도 되고 그 이후면 거절합니다.
- applicationStartsAt, createdAt, respondedAt, submissionCompletedAt 등 다른 시각 필드는 유지합니다.
- 기존 deadline에 날짜+시각을 보내던 클라이언트는 날짜만 보내도록 변경해야 합니다.
- 외부 API의 월·일만 있는 문자열은 연도 확인이 별도로 필요합니다. 마감 시각 제거가 연도 미확인 문제까지 해결하지는 않습니다.

## 기존 DB

Entity 변경만으로 기존 DB 열이 DATE로 바뀌었다고 가정하지 않습니다.
현재 H2 환경용 변환 SQL은 [마감일 변환 SQL](sql/announcement-deadline-date-h2.sql)에 있습니다.
앱을 중지하고 DB를 백업한 뒤 기존 TIMESTAMP 열을 DATE로 변환합니다. 기존 날짜는 보존되고 시각은 제거됩니다.
이 SQL은 자동 실행하지 않습니다. Hibernate ddl-auto=update 사용 시에도 재시작 후 실제 열 타입을 확인해야 합니다.
운영 DB가 H2와 다르면 해당 DB에 맞는 마이그레이션이 필요합니다.
