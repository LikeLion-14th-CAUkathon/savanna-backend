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
        if (isOnline(announcement)) {
            conditions.add(new Condition("PARTICIPATION_REGION", SATISFIED,
                    "온라인 공고는 지역과 관계없이 참여할 수 있습니다."));
        } else {
            String attendanceRegion = announcement.getRegion();
            if ((attendanceRegion == null || attendanceRegion.isBlank())
                    && announcement.getVenueNote() != null
                    && announcement.getVenueNote().contains("오프라인")) {
                attendanceRegion = announcement.getVenueNote();
            }
            if (attendanceRegion != null && !attendanceRegion.isBlank()) {
                Set<String> required = regionCodes(attendanceRegion);
                boolean matched = !required.isEmpty() && team != null
                        && team.memberEnrollmentStatuses() != null
                        && !team.memberEnrollmentStatuses().isEmpty()
                        && team.memberRegions() != null
                        && team.memberRegions().size() == team.memberEnrollmentStatuses().size()
                        && team.memberRegions().stream().allMatch(regions -> regions != null
                        && regions.stream().anyMatch(region -> regionCodes(region).stream().anyMatch(required::contains)));
                conditions.add(new Condition("PARTICIPATION_REGION", matched ? SATISFIED : TEAM_INFO_REQUIRED,
                        matched ? "모든 팀원이 필수 참석 지역에 참석할 수 있습니다."
                                : "필수 참석 지역과 팀원의 참석 가능 지역을 확인해 주세요: " + attendanceRegion));
            }
        }
        if (team == null || team.memberEnrollmentStatuses() == null
                || team.memberEnrollmentStatuses().isEmpty()) {
            conditions.add(new Condition("TEAM", TEAM_INFO_REQUIRED, "팀 및 가입된 팀원 정보를 확인해야 합니다."));
        } else {
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

    // 접수 방법이 온라인인 것과 온라인 공고인 것은 구분합니다.
    private boolean isOnline(Announcement announcement) {
        return containsOnline(announcement.getParticipationRegion())
                || containsOnline(announcement.getRegion())
                || containsOnline(announcement.getVenueNote());
    }

    private boolean containsOnline(String value) {
        return value != null && value.replaceAll("[\\p{Z}\\s\\u200B\\uFEFF]+", "").contains("온라인");
    }

    private Set<String> regionCodes(String value) {
        var result = new java.util.HashSet<String>();
        if (value == null) return result;
        String normalized = value.replaceAll("[\\p{Z}\\s\\u200B\\uFEFF]+", "").toUpperCase(java.util.Locale.ROOT);
        String[][] labels = {
                {"SEOUL", "서울"}, {"BUSAN", "부산"}, {"DAEGU", "대구"}, {"INCHEON", "인천"},
                {"GWANGJU", "광주"}, {"DAEJEON", "대전"}, {"ULSAN", "울산"}, {"SEJONG", "세종"},
                {"GYEONGGI", "경기"}, {"GANGWON", "강원"}, {"CHUNGBUK", "충북", "충청북도"},
                {"CHUNGNAM", "충남", "충청남도"}, {"JEONBUK", "전북", "전라북도"},
                {"JEONNAM", "전남", "전라남도"}, {"GYEONGBUK", "경북", "경상북도"},
                {"GYEONGNAM", "경남", "경상남도"}, {"JEJU", "제주"}
        };
        for (var aliases : labels) {
            if (normalized.equals(aliases[0]) || java.util.Arrays.stream(aliases).skip(1).anyMatch(normalized::contains))
                result.add(aliases[0]);
        }
        // '경기 광주'의 광주시는 광주광역시와 구분합니다.
        if (result.contains("GYEONGGI") && !normalized.contains("광주광역시")) result.remove("GWANGJU");
        return result;
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
