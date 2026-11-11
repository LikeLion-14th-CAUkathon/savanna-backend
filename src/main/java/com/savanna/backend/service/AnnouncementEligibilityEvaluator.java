package com.savanna.backend.service;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.dto.response.AnnouncementEligibilityResponse;
import com.savanna.backend.dto.response.AnnouncementEligibilityResponse.Condition;
import com.savanna.backend.enums.EligibilityStatus;
import com.savanna.backend.enums.EnrollmentStatus;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.savanna.backend.enums.EligibilityStatus.*;

@Component
public class AnnouncementEligibilityEvaluator {
    public AnnouncementEligibilityResponse evaluate(
            Announcement announcement, Long requestedTeamId, EligibilityTeamReader.TeamSnapshot team) {
        List<Condition> conditions = new ArrayList<>();
        if (announcement.getEnrollmentRequirement() != null
                && announcement.getEnrollmentRequirement().contains("해당자")) {
            conditions.add(new Condition("ADDITIONAL_REQUIREMENTS", TEAM_INFO_REQUIRED,
                    "참가 대상에 별도 조건이 있습니다. 공고 상세 원문을 확인해야 합니다."));
        }
        String participationRegion = announcement.getParticipationRegion();
        if (participationRegion != null && !participationRegion.isBlank()
                && !Set.of("온라인", "전국").contains(participationRegion.replaceAll("\\s+", ""))) {
            conditions.add(new Condition("PARTICIPATION_REGION", TEAM_INFO_REQUIRED,
                    "참가 가능 지역은 " + participationRegion + "입니다. 공고 원문에서 지역 자격을 확인해야 합니다."));
        }
        if (team == null || team.memberEnrollmentStatuses() == null
                || team.memberEnrollmentStatuses().isEmpty()) {
            conditions.add(new Condition("TEAM", TEAM_INFO_REQUIRED, "팀 및 가입된 팀원 정보를 확인해야 합니다."));
        } else {
            int count = team.memberEnrollmentStatuses().size();
            Integer min = announcement.getMinMembers();
            Integer max = announcement.getMaxMembers();
            if ((min != null && min < 1) || (max != null && max < 1)
                    || (min != null && max != null && min > max)) {
                conditions.add(new Condition("MEMBER_COUNT", TEAM_INFO_REQUIRED, "공고의 참가 인원 조건을 확인해야 합니다."));
            } else if ((min != null && count < min) || (max != null && count > max)) {
                conditions.add(new Condition("MEMBER_COUNT", MISMATCH, "현재 팀원 수가 공고의 참가 인원 범위와 일치하지 않습니다."));
            } else if (min == null || max == null) {
                conditions.add(new Condition("MEMBER_COUNT", TEAM_INFO_REQUIRED, "공고의 참가 인원 조건이 확인되지 않았습니다."));
            } else {
                conditions.add(new Condition("MEMBER_COUNT", SATISFIED, "현재 팀원 수가 확인된 참가 인원 조건을 충족합니다."));
            }
            Set<EnrollmentStatus> allowed = allowedStatuses(announcement.getEnrollmentRequirement());
            if (announcement.getAllowedStatusRaw() != null) {
                allowed = switch (announcement.getAllowedStatusRaw()) {
                    case "any" -> Set.of(EnrollmentStatus.values());
                    case "enrolled" -> Set.of(EnrollmentStatus.ENROLLED);
                    case "enrolled|on_leave" -> Set.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ON_LEAVE);
                    default -> null;
                };
            }
            if (allowed == null || team.memberEnrollmentStatuses().stream().anyMatch(s -> s == null)) {
                conditions.add(new Condition("ENROLLMENT", TEAM_INFO_REQUIRED, "참가 대상 원문과 팀원의 재학 상태를 확인해야 합니다."));
            } else if (!allowed.containsAll(team.memberEnrollmentStatuses())) {
                conditions.add(new Condition("ENROLLMENT", MISMATCH, "참가 대상의 재학 상태 조건과 일치하지 않는 팀원이 있습니다."));
            } else {
                conditions.add(new Condition("ENROLLMENT", SATISFIED, "모든 팀원이 재학 상태 조건을 충족합니다."));
            }
        }
        EligibilityStatus status = conditions.stream().anyMatch(c -> c.status() == MISMATCH)
                ? MISMATCH : conditions.stream().anyMatch(c -> c.status() == TEAM_INFO_REQUIRED)
                ? TEAM_INFO_REQUIRED : SATISFIED;
        return new AnnouncementEligibilityResponse(announcement.getAnnouncementId(),
                team == null ? requestedTeamId : team.teamId(), status, List.copyOf(conditions));
    }

    // 문장 일부를 검색해 자격을 단정하지 않습니다. 명확한 전체 문자열만 지원합니다.
    private Set<EnrollmentStatus> allowedStatuses(String requirement) {
        if (requirement == null) return null;
        return switch (requirement.trim()) {
            case "재학생", "재학" -> Set.of(EnrollmentStatus.ENROLLED);
            case "휴학생", "휴학" -> Set.of(EnrollmentStatus.ON_LEAVE);
            case "졸업생", "졸업" -> Set.of(EnrollmentStatus.GRADUATED);
            case "재학생 및 휴학생", "재학생 또는 휴학생" ->
                    Set.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.ON_LEAVE);
            case "제한 없음", "누구나" -> Set.of(EnrollmentStatus.values());
            default -> null;
        };
    }
}
