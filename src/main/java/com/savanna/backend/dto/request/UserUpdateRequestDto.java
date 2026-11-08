package com.savanna.backend.dto.request;

import com.savanna.backend.enums.EnrollmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UserUpdateRequestDto(

        @NotBlank
        String name,

        String universityName,

        @NotNull
        EnrollmentStatus enrollmentStatus,

        @NotEmpty
        List<@NotBlank String> regions
) {}
