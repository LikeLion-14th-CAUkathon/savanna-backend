package com.savanna.backend.service;

import com.savanna.backend.dto.response.AnnouncementEligibilityResponse;
import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.enums.RecommendationSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Provider adapter must use bounded timeouts and return facts grounded in these inputs. */
public interface RecommendationAiClient {
    record TeamInput(String projectName, String projectIntro, String stack, List<EnrollmentStatus> enrollmentStatuses) {}
    record Candidate(Long announcementId, String title, AnnouncementField field, String theme,
                     String evaluationCriteria, String submissionRequirements, String enrollmentRequirement,
                     Integer minMembers, Integer maxMembers, String participationRegion,
                     LocalDateTime applicationStartsAt, LocalDate deadline, AnnouncementEligibilityResponse eligibility) {}
    record Suggestion(Long announcementId, int score, String reason) {}

    List<Suggestion> recommend(TeamInput team, List<Candidate> candidates);
    default RecommendationSource source() { return RecommendationSource.AI; }
}
