package com.savanna.backend.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태에서 요청을 처리할 수 없습니다."),
    ALREADY_IN_TEAM(HttpStatus.CONFLICT, "이미 소속된 팀이 있습니다. 한 계정은 한 팀에만 소속될 수 있습니다."),
    TEAM_LAST_MEMBER_CANNOT_LEAVE(HttpStatus.CONFLICT, "마지막 팀원은 승계 대상이 없어 탈퇴할 수 없습니다."),
    RECOMMENDATION_AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "추천 AI가 연결되지 않았거나 일시적으로 사용할 수 없습니다."),
    RECOMMENDATION_AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "추천 AI의 응답을 확인할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}

