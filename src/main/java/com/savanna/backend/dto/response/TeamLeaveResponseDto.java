package com.savanna.backend.dto.response;
public record TeamLeaveResponseDto(Long teamId,Long userId,
    @io.swagger.v3.oas.annotations.media.Schema(description="팀장 탈퇴로 승계된 사용자의 ID. 일반 팀원 탈퇴는 null",nullable=true)
    Long newLeaderUserId,
    @io.swagger.v3.oas.annotations.media.Schema(description="마지막 팀원 탈퇴로 팀과 연결 기록을 실제 삭제했으면 true")
    boolean teamDeleted) {}
