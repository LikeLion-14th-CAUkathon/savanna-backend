package com.savanna.backend.domain;

import com.savanna.backend.enums.TeamRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "team_member",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "team_id"}
        )
)
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long teamMemberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamRole role;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    public TeamMember(
            User user,
            Team team,
            TeamRole role
    ) {
        this.user = user;
        this.team = team;
        this.role = role;
        this.joinedAt = LocalDateTime.now();
    }
}
