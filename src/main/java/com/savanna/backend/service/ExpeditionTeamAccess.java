package com.savanna.backend.service;

import java.security.Principal;

/**
 * 팀 담당 구현이 제공할 권한 연동 지점.
 * 해당 팀의 조회/변경 권한을 확인하고 인증 사용자의 실제 userId를 반환해야 합니다.
 * 권한 없으면 BusinessException(FORBIDDEN)을 발생시킵니다.
 * 클라이언트가 보낸 userId나 Principal 이름을 숫자로 변환하여 신뢰하지 않습니다.
 */
public interface ExpeditionTeamAccess {
    Long requireMember(Principal principal, Long teamId, boolean write);
}
