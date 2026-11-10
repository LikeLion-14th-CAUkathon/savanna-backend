package com.savanna.backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "team")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long teamId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String projectName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String projectIntro;

    @Column(length = 2048)
    private String githubUrl;

    @Column(length = 2048)
    private String presentationUrl;

    @Column(length = 500)
    private String stack;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Team(
            String name,
            String projectName,
            String projectIntro,
            String githubUrl,
            String presentationUrl,
            String stack
    ) {
        this.name = name;
        this.projectName = projectName;
        this.projectIntro = projectIntro;
        this.githubUrl = githubUrl;
        this.presentationUrl = presentationUrl;
        this.stack = stack;
        this.createdAt = LocalDateTime.now();
    }

    public void update(
            String name,
            String projectName,
            String projectIntro,
            String githubUrl,
            String presentationUrl,
            String stack
    ) {
        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException(
                        "팀 이름은 비워둘 수 없습니다."
                );
            }
            this.name = name.trim();
        }

        if (projectName != null) {
            if (projectName.isBlank()) {
                throw new IllegalArgumentException(
                        "프로젝트 이름은 비워둘 수 없습니다."
                );
            }
            this.projectName = projectName.trim();
        }

        if (projectIntro != null) {
            if (projectIntro.isBlank()) {
                throw new IllegalArgumentException(
                        "프로젝트 소개는 비워둘 수 없습니다."
                );
            }
            this.projectIntro = projectIntro.trim();
        }


        if (githubUrl != null) {
            this.githubUrl = normalizeOptional(githubUrl);
        }

        if (presentationUrl != null) {
            this.presentationUrl = normalizeOptional(presentationUrl);
        }

        if (stack != null) {
            this.stack = normalizeOptional(stack);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
