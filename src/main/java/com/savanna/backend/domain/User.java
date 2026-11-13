
package com.savanna.backend.domain;

import com.savanna.backend.enums.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String googleSub;

    private String name;

    private String universityName;

    @Enumerated(EnumType.STRING)
    private EnrollmentStatus enrollmentStatus;

    // 사용자가 선택한 동물 캐릭터 (1~9)
    private Integer characterId;

    @Column(nullable = false)
    private boolean onboardingCompleted = false;

    public User(String email, String googleSub) {
        this(email, googleSub, null);
    }

    public User(
            String email,
            String googleSub,
            String googleName
    ) {
        this.email = email;
        this.googleSub = googleSub;
        this.name = googleName;
    }

    // 기존 프로필 수정 메서드 유지
    public void updateProfile(
            String name,
            EnrollmentStatus enrollmentStatus
    ) {
        if (name != null) {
            this.name = name;
        }

        if (enrollmentStatus != null) {
            this.enrollmentStatus = enrollmentStatus;
        }
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    public void updateProfile(
            String name,
            String universityName,
            EnrollmentStatus enrollmentStatus
    ) {
        if (name != null) {
            this.name = name.trim();
        }

        if (universityName != null) {
            this.universityName = universityName;
        }

        if (enrollmentStatus != null) {
            this.enrollmentStatus = enrollmentStatus;
        }

        this.onboardingCompleted = true;
    }

    // 캐릭터 수정
    public void updateCharacter(Integer characterId) {
        if (characterId != null) {
            this.characterId = characterId;
        }
    }
}