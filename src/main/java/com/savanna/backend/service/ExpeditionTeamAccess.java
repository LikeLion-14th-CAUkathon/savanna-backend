package com.savanna.backend.service;
import java.security.Principal;

/** 실제 팀 구현에서 권한을 검증하고 인증 사용자 ID를 반환합니다. 접근 불가는 FORBIDDEN입니다. */
public interface ExpeditionTeamAccess {
    Long requireMember(Principal principal, Long teamId, boolean write);
}
