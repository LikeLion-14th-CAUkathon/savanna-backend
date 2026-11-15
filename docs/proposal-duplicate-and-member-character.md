# 프런트 문의: 중복 제안과 팀원 캐릭터

## 같은 공고를 두 번 제안하면

POST /api/v1/announcements/{announcementId}/proposals는 같은 팀·공고에 원정이 이미 있으면 409 Conflict를 반환합니다. 같은 발신자의 재시도, 다른 메시지, 같은 팀의 다른 발신자도 동일합니다. 새 원정·제안·수신 기록은 생성하지 않습니다. 다른 팀은 같은 공고에 별도로 제안할 수 있습니다.

```json
{"code":"CONFLICT","message":"현재 상태에서 요청을 처리할 수 없습니다.","errors":[]}
```

공고 쓰기 잠금 아래 기존 원정을 확인하고 DB에도 팀·공고 유니크 제약이 있습니다. 기존 중복 거절 동작을 유지하고 Swagger에 201/400/401/403/404/409를 명시했습니다. 409는 수신자 정보 변경 등 다른 충돌에도 쓰이므로 HTTP 상태만으로 모든 충돌 원인을 단정하지 않습니다.

## 팀원 캐릭터: 구현 전 확인과 보완

현재 GET /api/v1/teams/{teamId}의 members 항목에는 userId/name/role만 있습니다. User 엔티티, 프로필 변경 요청·조회 응답에도 characterId가 없고 캐릭터 ID 정의·저장 경로가 발견되지 않았습니다. 로컬에 있는 origin/feat/user, origin/feat/team, origin/develop 참조의 src/main/java에서도 관련 필드를 찾지 못했습니다. 원격 브랜치를 fetch하거나 병합한 것은 아닙니다.

이후 공개 배포 프런트의 /assets/index-MT05xv2v.js를 읽어 선택 화면이 숫자 apiId(1~9)를 전송하고 있음을 확인했습니다. 외부 JS를 실행하거나 프런트를 변경한 것은 아닙니다. 이 규격을 기준으로 선택 ID 저장 → 내 정보 응답 → members[].characterId 전달을 구현했습니다. 임의 ID·랜덤 캐릭터를 배정하지 않습니다.

| 숫자 characterId | 프런트 내부 코드 | 캐릭터 |
|---|---|---|
| 1 | baby-lion | 아기사자 |
| 2 | adult-lion | 어른사자 |
| 3 | elephant | 코끼리 |
| 4 | giraffe | 기린 |
| 5 | zebra | 얼룩말 |
| 6 | meerkat | 미어캣 |
| 7 | cheetah | 치타 |
| 8 | hippo | 하마 |
| 9 | ostrich | 타조 |

### 변경 API

- PATCH /api/v1/me: **필수 입력 characterId** 추가. 기존 name 필수 검증도 유지합니다. 1~9만 허용하며 생략/null/범위 밖 값은 400입니다. 다른 프로필 정보만 수정하더라도 현재 선택한 characterId를 함께 전송합니다.
- GET /api/v1/me 및 PATCH 응답: data.characterId 추가.
- GET /api/v1/teams/{teamId} 및 팀 정보 PATCH 응답: data.members[].characterId 추가. 조회자의 캐릭터를 복사하지 않고 각 User의 저장값을 반환합니다.

내 정보·팀 상세의 기존 status/message/data JSON 구조는 유지하고, 서버 반환 타입을 DataResponse<실제 DTO>로 명시하여 Swagger에도 UserResponseDto 및 팀원 MemberInfo의 새 필드가 나타나게 했습니다.

프로필 저장 요청 예시:

```json
{"name":"김사바","characterId":3}
```

팀 상세 응답의 members 예시:

```json
[
  {"userId":1,"name":"김사바","role":"LEADER","characterId":3},
  {"userId":2,"name":"팀원","role":"MEMBER","characterId":null}
]
```

API 값은 **숫자**입니다. 프런트 이미지 컴포넌트가 baby-lion/elephant 같은 문자열을 사용한다면 API의 숫자를 characters.find(c => c.apiId === member.characterId)?.id로 변환해서 넘겨야 합니다. 현재 공개 배포 UI에는 문자열 id로 이미지를 찾는 코드가 있어 숫자를 그대로 넘기면 여전히 기본 아기사자로 표시될 수 있습니다. 프런트 저장소는 이 작업 공간에 없어 이 변환 코드는 변경하지 않았습니다.

### 기존 사용자와 DB

기존에는 저장 필드가 없어 과거 선택값을 서버에서 복원할 수 없습니다. 기존 사용자는 null이며, 프로필에서 캐릭터를 다시 저장하면 이후 조회에 반영됩니다. null을 실제 선택한 아기사자(1)로 간주하지 않습니다. 화면의 미설정 기본 표시는 프런트에서 구분합니다.

캐릭터 선택은 필수입니다. 프런트는 미선택 시 저장/시작 버튼을 막고 캐릭터 선택을 안내해야 합니다. 확인한 공개 배포 코드에는 미선택 시 랜덤 캐릭터를 골라 보내는 처리가 있으므로 이 fallback을 제거해야 합니다. 서버는 전송된 ID의 존재/허용 범위를 검증하며 실제 클릭 여부를 판별할 수는 없습니다. 프런트 저장소는 이 작업 공간에 없어 해당 UI는 변경하지 않았습니다.

새 컬럼은 users.character_id(nullable INTEGER)이고 [H2 SQL](sql/user-character-id-h2.sql)을 준비했습니다. 운영 서비스 정상 중지 → DB 백업 → 컬럼/제약 추가 → 새 JAR 배포 → 재시작 순서로 적용합니다. SQL은 기존 사용자를 삭제하거나 캐릭터를 일괄 설정하지 않습니다.

EC2 배포·DB 변경은 실행하지 않았습니다.

2026-11-14 검증: JWT HTTP 요청으로 최초 제안 201/재전송 409/다른 팀 201 및 기록 수 유지, 프로필 선택 저장·DB 재조회·멤버별 캐릭터 반환·기존 미설정 null·범위 검증과 Swagger 스키마를 확인했습니다. 캐릭터 필수 정책에 맞춰 생략/null 요청은 400이고 기존 저장값을 덮어쓰지 않는 테스트로 변경했습니다. H2 컬럼 SQL의 기존 사용자 보존·반복 적용도 검증했습니다. 프런트 실제 화면과 EC2는 배포 후 별도 확인이 필요합니다.
