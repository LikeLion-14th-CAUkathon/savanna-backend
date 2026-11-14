package com.savanna.backend.repository;
import com.savanna.backend.domain.AnnouncementSourceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AnnouncementSourceEventRepository extends JpaRepository<AnnouncementSourceEvent, Long> {}
