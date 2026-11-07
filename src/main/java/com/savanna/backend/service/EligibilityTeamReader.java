package com.savanna.backend.service;

import com.savanna.backend.enums.EnrollmentStatus;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * Team 담당 구현에서 제공할 읽기 전용 연동 지점.
 * 요청자의 팀 접근 권한을 검증한 후 실제 가입된 팀원 전체를 반환해야 합니다.
 * teamId가 null이면 현재 팀을 선택합니다. 접근 불가는 FORBIDDEN,
 * 지정된 팀이 없으면 RESOURCE_NOT_FOUND를 발생시켜야 합니다.
 * 정보 미완성은 Optional.empty()로 표현합니다. 데이터를 저장하지 않습니다.
 */
public interface EligibilityTeamReader {
    Optional<TeamSnapshot> read(Principal requester, Long teamId);

    record TeamSnapshot(Long teamId, List<EnrollmentStatus> memberEnrollmentStatuses) {}
}