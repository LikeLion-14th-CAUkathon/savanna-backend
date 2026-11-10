package com.savanna.backend.domain;

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
@Table(name = "proposals", indexes = @Index(name = "idx_proposals_expedition", columnList = "expedition_id"))
public class Proposal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long proposalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expedition_id", nullable = false, updatable = false,unique= true)
    private Expedition expedition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Proposal(Expedition expedition, User sender, String message) {
        if (expedition == null || sender == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        this.expedition = expedition;
        this.sender = sender;
        this.message = message;
    }

    @PrePersist
    private void onCreate() { createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")); }
}
