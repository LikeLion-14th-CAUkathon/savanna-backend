package com.savanna.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequestDto(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 150)
        String projectName,

        @NotBlank
        String projectIntro,

        @Size(max = 2048)
        String githubUrl,

        @Size(max = 2048)
        String presentationUrl,

        @Size(max = 500)
        String stack
) {}
