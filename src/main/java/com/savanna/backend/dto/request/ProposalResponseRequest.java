package com.savanna.backend.dto.request;

import com.savanna.backend.enums.ProposalRecipientStatus;
import jakarta.validation.constraints.NotNull;

public record ProposalResponseRequest(@NotNull ProposalRecipientStatus status) {}
