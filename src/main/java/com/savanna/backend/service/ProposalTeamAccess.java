package com.savanna.backend.service;

import java.security.Principal;
import java.util.List;

public interface ProposalTeamAccess {

    ProposalTeamContext requireTeam(Principal principal);

    default ProposalTeamContext requireTeam(Principal principal, Long teamId) {
        return requireTeam(principal);
    }

    record ProposalTeamContext(
            Long teamId,
            Long senderId,
            List<Long> recipientIds
    ) {
    }
}
