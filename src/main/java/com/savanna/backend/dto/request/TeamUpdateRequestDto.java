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
        String stack,

        @Size(max = 2048)
        @Pattern(regexp="^$",message="사진 URL 직접 입력은 지원하지 않습니다. 사진 업로드 API를 사용해 주세요.")
        @io.swagger.v3.oas.annotations.media.Schema(description="사진은 팀 생성 후 POST /api/v1/teams/{teamId}/photo로 업로드합니다. 생략/null은 유지, 빈 문자열은 삭제. URL 직접 입력 불가",deprecated=true)
        String photoUrl
) {}
