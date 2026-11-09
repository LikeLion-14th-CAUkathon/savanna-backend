package com.savanna.backend.dto.request;

import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ApplicationMethod;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record AnnouncementCreateRequest(
        @NotBlank
        @Size(max = 300)
        String title,

        LocalDateTime applicationStartsAt,

        @NotNull
        LocalDateTime deadline,

        @NotNull
        AnnouncementField field,

        @NotBlank
        @Size(max = 255)
        String host,

        @Min(1)
        Integer minMembers,

        @Min(1)
        Integer maxMembers,

        String evaluationCriteria,
        String enrollmentRequirement,

        @Size(max = 255)
        String region,

        String theme,
        ApplicationMethod applicationMethod,
        String submissionRequirements,

        @Size(max = 2000)
        String sourceUrl,

        @Size(max = 2000)
        String applyUrl
) {
    @AssertTrue(message = "접수 마감은 접수 시작보다 늦어야 합니다.")
    public boolean isApplicationPeriodValid() {
        return applicationStartsAt == null
                || deadline == null
                || deadline.isAfter(applicationStartsAt);
    }

    @AssertTrue(message = "최대 인원은 최소 인원 이상이어야 합니다.")
    public boolean isMemberRangeValid() {
        return minMembers == null
                || maxMembers == null
                || minMembers <= maxMembers;
    }
}