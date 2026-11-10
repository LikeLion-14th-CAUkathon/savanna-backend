package com.savanna.backend.dto.request;

import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.enums.RegionCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

public record UserUpdateRequestDto(
        @NotBlank String name,
        String universityName,
        EnrollmentStatus enrollmentStatus,
        List<@NotBlank String> regions
) {
        @AssertTrue(message = "올바르지 않은 지역 코드가 포함되어 있습니다.")
        public boolean isValidRegions() {
                if (regions == null) {
                        return true;
                }

                Set<String> allowedRegions = Arrays.stream(
                        RegionCode.values()
                ).map(Enum::name).collect(Collectors.toSet());

                return regions.stream()
                        .allMatch(region ->
                                region != null
                                        && allowedRegions.contains(region.trim())
                        );
        }
}
