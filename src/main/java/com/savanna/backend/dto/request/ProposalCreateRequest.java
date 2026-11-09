package com.savanna.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposalCreateRequest(

        @NotBlank(message = "제안 내용을 입력해주세요.")
        @Size(
                max = 2000,
                message = "제안 내용은 2000자 이하여야 합니다."
        )
        String message,
        @jakarta.validation.constraints.Positive Long teamId

) {
    public ProposalCreateRequest(String message) {
        this(message, null);
    }
}
