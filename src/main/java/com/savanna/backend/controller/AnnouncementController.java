package com.savanna.backend.controller;

import com.savanna.backend.dto.response.AnnouncementResponse;
import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ReceptionStatus;
import com.savanna.backend.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public Page<AnnouncementResponse> getAnnouncements(
            @RequestParam(name = "keyword", required = false)
            String keyword,

            @RequestParam(name = "field", required = false)
            AnnouncementField field,

            @RequestParam(name = "status", required = false)
            ReceptionStatus status,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "10")
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page는 0 이상, size는 1~100이어야 합니다."
            );
        }

        return announcementService.getAnnouncements(
                keyword,
                field,
                status,
                page,
                size
        );
    }
}