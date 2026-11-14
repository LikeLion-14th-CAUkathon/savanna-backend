package com.savanna.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TeamUpdateRequestDto(

        @Size(max = 100)
        String name,

        @Size(max = 150)
        String projectName,

        String projectIntro,

        @Size(max = 2048)
        @Pattern(
                regexp = "^(https?://[^\\s]+)?$",
                message = "http:// 또는 https://로 시작하는 URL을 입력해 주세요."
        )
        String githubUrl,

        @Size(max = 2048)
        @Pattern(
                regexp = "^(https?://[^\\s]+)?$",
                message = "http:// 또는 https://로 시작하는 URL을 입력해 주세요."
        )
        String presentationUrl,

        @Size(max = 500)
        String stack
) {}
