package com.savanna.backend.domain;

import com.savanna.backend.enums.RecommendationSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "announcement_recommendations", uniqueConstraints = @UniqueConstraint(
        name = "uk_recommendation_team_announcement", columnNames = {"team_id", "announcement_id"}))
public class AnnouncementRecommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recommendationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false, length = 64)
    private String inputHash;
    @Column(nullable = false, length = 64)
    private String announcementHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private RecommendationSource source;
    @Column(nullable = false)
    private LocalDateTime generatedAt;

    public AnnouncementRecommendation(Team team, Announcement announcement, int score, String reason,
            String inputHash, String announcementHash, RecommendationSource source, LocalDateTime generatedAt) {
        this.team = team;
        this.announcement = announcement;
        this.score = score;
        this.reason = reason;
        this.inputHash = inputHash;
        this.announcementHash = announcementHash;
        this.source = source;
        this.generatedAt = generatedAt;
    }
}
