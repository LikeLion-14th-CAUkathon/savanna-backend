
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
        Integer characterId,
        boolean onboardingCompleted
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
                user.getCharacterId(),
                user.isOnboardingCompleted()
        );
    }
}