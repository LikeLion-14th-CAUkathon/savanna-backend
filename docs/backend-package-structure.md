# C·D 백엔드 패키지 구조

기본 경로: `src/main/java/com/savanna/backend`

도메인별 하위 패키지를 만들지 않고, 각 계층 패키지 바로 아래에 Java 파일을 둡니다.

```text
com/savanna/backend/
  domain/
    Announcement.java
    AnnouncementRecommendation.java
    Proposal.java
    ProposalRecipient.java
    Expedition.java
    ExpeditionStageHistory.java
    ExpeditionTask.java
    ExpeditionChecklist.java
    User.java
    UserRegion.java
  controller/
    AnnouncementController.java
    ...
  service/
    AnnouncementService.java
    ...
  repository/
    AnnouncementRepository.java
    ...
    UserRepository.java
    UserRegionRepository.java
  dto/
    request/
      AnnouncementRequest.java
      ...
    response/
      AnnouncementResponse.java
      ...
  enums/
    EnrollmentStatus.java
  error/
    BusinessException.java
    ErrorCode.java
    ErrorResponse.java
    GlobalExceptionHandler.java
```

C·D의 8개 도메인 각각에 엔티티용 클래스, Controller, Service, Repository 인터페이스, Request 및 Response 클래스를 같은 규칙으로 생성했습니다. 기존 User 관련 파일은 변경하지 않았습니다.

추가된 C·D 파일은 구조용 빈 클래스·인터페이스입니다. JPA 매핑, repository의 JpaRepository 상속, Spring 빈 등록, DTO 필드, API 및 비즈니스 로직은 후속 구현 대상입니다. DTO도 실제 API 구현 시 요청·응답 용도별 이름과 필드를 구체화합니다. 공통 MVC 에러 처리는 구현되어 있습니다.

## 에러 사용

서비스에서 `throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);`처럼 사용합니다. 구체적인 도메인 오류는 `ErrorCode`에 추가합니다.

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "요청한 정보를 찾을 수 없습니다.",
  "errors": []
}
```

`@Valid` 요청 DTO 검증 실패 시 `errors`에 `field`, `message`를 제공합니다. 잘못된 JSON, 타입 불일치, 지원하지 않는 HTTP 메서드 등 MVC 예외는 원래 HTTP 상태를 유지합니다. Security 필터의 인증·인가 실패 처리는 추후 A·B의 `AuthenticationEntryPoint`, `AccessDeniedHandler`에서 같은 응답 형식으로 연결해야 합니다.

## 후속 구현 시 연동 기준

- 제안 응답과 TeamMember 추가는 같은 트랜잭션으로 처리합니다. A·B의 팀 가입 규칙 확정 후 연동합니다.
- Expedition 최초 생성과 제출물 기반 체크리스트 생성은 같은 트랜잭션으로 처리합니다.
- 단계 변경과 이력 기록, 제출 완료 시각 저장은 같은 트랜잭션으로 처리합니다.
- 추천 이유 생성은 실제 AI 연동이 필요하며, 임의 문자열을 AI 응답으로 저장하지 않습니다.
- 팀 관련 API는 A·B 인증 및 팀 권한 검증이 연결된 후 공개합니다.
