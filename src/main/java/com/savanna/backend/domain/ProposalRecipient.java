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
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("0")
    private long responseVersion;
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("0")
    private long responseReadVersion;
    private LocalDateTime responseReadAt;

    public ProposalRecipient(Proposal proposal, User user) {
        if (proposal == null || user == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        this.proposal = proposal;
        this.user = user;
    }

    /** 상태가 바뀐 경우에만 응답 시각과 버전을 갱신합니다. 팀 탈퇴를 의미하지 않습니다. */
    public boolean respond(ProposalRecipientStatus status) {
        if (status == null || status == ProposalRecipientStatus.PENDING)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        markAsRead();
        if (this.status == status) return false;
        long previousVersion = getResponseVersion();
        this.status = status;
        this.respondedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        this.responseVersion = previousVersion + 1;
        return true;
    }

    public void markAsRead() {
        if (readAt == null) readAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    // 기존 응답 데이터의 새 버전 컬럼이 0이어도 최초 응답 버전 1로 취급합니다.
    public long getResponseVersion() {
        return status == ProposalRecipientStatus.PENDING ? 0 : Math.max(1, responseVersion);
    }

    public boolean isResponseUnread() {
        return getResponseVersion() > responseReadVersion;
    }

    /** 화면에서 실제 확인한 버전까지만 읽습니다. 이후 도착한 응답을 지우지 않습니다. */
    public boolean markResponseRead(long observedVersion) {
        if (observedVersion <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (observedVersion > getResponseVersion()) throw new BusinessException(ErrorCode.CONFLICT);
        if (observedVersion <= responseReadVersion) return false;
        responseReadVersion = observedVersion;
        responseReadAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        return true;
    }
}
