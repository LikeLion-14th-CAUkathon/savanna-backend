package com.savanna.backend.dto.request;

import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ApplicationMethod;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record AnnouncementCreateRequest(
        @NotBlank
        @Size(max = 300)
        String title,

        LocalDateTime applicationStartsAt,

        @NotNull
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate deadline,

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
        String applyUrl,

        @Size(max = 10000)
        @io.swagger.v3.oas.annotations.media.Schema(description = "지원 방법의 구체적인 안내 문장. 미확인은 null")
        String applicationInstructions,

        @Size(max = 10000)
        @io.swagger.v3.oas.annotations.media.Schema(description = "제출 양식 안내. 미확인은 null")
        String formNote,

        @Size(max = 10000)
        String lengthNote,

        @Size(max = 10000)
        @io.swagger.v3.oas.annotations.media.Schema(description = "공고가 요구하는 동의서 종류·서명·제출 안내. 사용자 동의 여부가 아니며 미확인은 null")
        String consentRequirements,

        @Size(max = 255)
        String participationRegion,

        @Size(max = 10000)
        String venueNote
) {
    @AssertTrue(message = "접수 마감일은 접수 시작일보다 빠를 수 없습니다.")
    public boolean isApplicationPeriodValid() {
        return applicationStartsAt == null
                || deadline == null
                || !deadline.isBefore(applicationStartsAt.toLocalDate());
    }

    @AssertTrue(message = "최대 인원은 최소 인원 이상이어야 합니다.")
    public boolean isMemberRangeValid() {
        return minMembers == null
                || maxMembers == null
                || minMembers <= maxMembers;
    }
}
