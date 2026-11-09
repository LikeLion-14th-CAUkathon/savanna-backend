package com.savanna.backend.domain;

import com.savanna.backend.enums.ProposalRecipientStatus;
import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "proposal_recipients",
        uniqueConstraints = @UniqueConstraint(name = "uk_proposal_recipient_user",
                columnNames = {"proposal_id", "user_id"}))
public class ProposalRecipient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long proposalRecipientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, updatable = false)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalRecipientStatus status = ProposalRecipientStatus.PENDING;
    private LocalDateTime readAt;
    private LocalDateTime respondedAt;

    public ProposalRecipient(Proposal proposal, User user) {
        if (proposal == null || user == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        this.proposal = proposal;
        this.user = user;
    }

    /** 응답 서비스가 JOINED 응답과 팀 가입을 같은 트랜잭션으로 처리합니다. */
    public void respond(ProposalRecipientStatus status) {
        if (status == null || status == ProposalRecipientStatus.PENDING)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (this.status != ProposalRecipientStatus.PENDING)
            throw new BusinessException(ErrorCode.CONFLICT);
        this.status = status;
        this.respondedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
