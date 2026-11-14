package com.savanna.backend.dto.response;

import com.savanna.backend.domain.User;
import com.savanna.backend.enums.EnrollmentStatus;

import java.util.List;

public record UserResponseDto(
        Long userId,
        String email,
        String name,
        String universityName,
        EnrollmentStatus enrollmentStatus,
        List<String> regions,
        boolean onboardingCompleted,
        @io.swagger.v3.oas.annotations.media.Schema(description="선택 캐릭터 숫자 ID(1~9). 미설정은 null",nullable=true)
        Integer characterId
) {
    public static UserResponseDto from(
            User user,
            List<String> regions
    ) {
        return new UserResponseDto(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getUniversityName(),
                user.getEnrollmentStatus(),
                regions,
                user.isOnboardingCompleted(),
                user.getCharacterId()
        );
    }
}
