package com.savanna.backend.enums;

@io.swagger.v3.oas.annotations.media.Schema(description="ACTIVE: 사용 가능, REVOKED: 팀장에 의해 폐기, EXPIRED: 과거 7일형 링크의 만료. 영구 링크는 자연 만료되지 않습니다.")
public enum TeamInvitationStatus { ACTIVE, REVOKED, EXPIRED }
