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

    @Column(nullable = false)
    private boolean onboardingCompleted = false;

    // 구글 로그인 성공 시 사용자 생성
    public User(String email, String googleSub) {
        this.email = email;
        this.googleSub = googleSub;
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
}