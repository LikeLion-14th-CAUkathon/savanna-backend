package com.savanna.backend.service;

import com.savanna.backend.domain.ProposalRecipient;
import com.savanna.backend.domain.User;
import com.savanna.backend.repository.ProposalRecipientRepository;
import com.savanna.backend.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ProposalRecipientSyncService {
    private final ProposalRepository proposals;
    private final ProposalRecipientRepository recipients;

    // 호출부에서 가입 승인 및 사용자·팀 잠금 후 실행
    public void registerExistingProposals(Long teamId, User user) {
        recipients.deleteRevokedByUserAndTeam(user.getUserId(), teamId);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (var proposal : proposals.findActiveProposalsByTeamId(teamId, today)) {
            if (!recipients.existsByProposal_ProposalIdAndUser_UserId(proposal.getProposalId(), user.getUserId())) {
                recipients.save(new ProposalRecipient(proposal, user));
            }
        }
    }
}
