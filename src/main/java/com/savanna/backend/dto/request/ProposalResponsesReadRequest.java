package com.savanna.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ProposalResponsesReadRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid Response> responses) {
    public record Response(@NotNull @Positive Long proposalRecipientId, @Positive long responseVersion) {}
}
