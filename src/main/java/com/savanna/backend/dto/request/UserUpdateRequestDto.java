package com.savanna.backend.dto.request;

import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.enums.RegionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record UserUpdateRequestDto(
        @NotBlank String name,
        String universityName,
        EnrollmentStatus enrollmentStatus,
        List<@NotBlank String> regions,

        @NotNull(message = "캐릭터를 선택해 주세요.")
        @Min(value = 1, message = "캐릭터 ID는 1 이상이어야 합니다.")
        @Max(value = 9, message = "캐릭터 ID는 9 이하여야 합니다.")
        @Schema(
                description = "필수 선택 캐릭터 숫자 ID(1~9). 생략/null은 400",
                allowableValues = {"1", "2", "3", "4", "5", "6", "7", "8", "9"}
        )
        Integer characterId
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