package com.savanna.backend.repository;

import com.savanna.backend.domain.UserRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRegionRepository
        extends JpaRepository<UserRegion, Long> {

    List<UserRegion> findByUser_UserId(Long userId);

    void deleteByUser_UserId(Long userId);
}
