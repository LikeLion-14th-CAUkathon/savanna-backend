package com.savanna.backend.repository;

import com.savanna.backend.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AnnouncementRepository
        extends JpaRepository<Announcement, Long>,
        JpaSpecificationExecutor<Announcement> {
}