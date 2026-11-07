package com.savanna.backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_region",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "region_code"}
        )
)
public class UserRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    public UserRegion(User user, String regionCode) {
        this.user = user;
        this.regionCode = regionCode;
    }
}
