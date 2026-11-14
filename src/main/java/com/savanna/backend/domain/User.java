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

    // 프런트 캐릭터 apiId(1~9). 미설정 사용자는 null이며 임의로 배정하지 않습니다.
    @Column(name = "character_id")
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

    // 프로필 수정
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

    public void updateCharacter(Integer characterId) {
        if(characterId==null)throw new IllegalArgumentException("캐릭터를 선택해 주세요.");
        if(characterId<1||characterId>9)throw new IllegalArgumentException("캐릭터 ID는 1~9여야 합니다.");
        this.characterId=characterId;
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
}
