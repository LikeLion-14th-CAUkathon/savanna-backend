package com.savanna.backend.dto.request;

import jakarta.validation.constraints.Size;

public record TeamUpdateRequestDto(

        @Size(max = 100)
        String name,

        @Size(max = 150)
        String projectName,

        String projectIntro,

        @Size(max = 2048)
        String githubUrl,

        @Size(max = 2048)
        String presentationUrl,

        @Size(max = 500)
        String stack
) {}
