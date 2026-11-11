package com.savanna.backend.integration.contestkorea;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContestKoreaWriter {
    private final AnnouncementRepository announcements;
    public record ImportResult(int created, int updated, List<String> skippedIds) {}

    @Transactional
    public ImportResult save(ContestKoreaCategory category, List<ContestKoreaItem> items) {
        if (category == null || items == null) throw new IllegalArgumentException("분야와 공고 목록이 필요합니다.");
        int created = 0, updated = 0;
        var skipped = new ArrayList<String>();
        for (var item : items) {
            ContestKoreaPeriod period;
            try {
                if (item == null || clean(item.id()) == null || item.id().trim().length() > 100
                        || clean(item.title()) == null || item.title().trim().length() > 300
                        || clean(item.host()) == null || item.host().trim().length() > 255
                        || !validUrl(item.sourceUrl()) || (clean(item.posterUrl()) != null && !validUrl(item.posterUrl()))
                        || tooLong(item.category()) || tooLong(item.participationRegion()) || tooLong(item.period())) {
                    throw new IllegalArgumentException();
                }
                period = ContestKoreaPeriod.parse(item.period());
            } catch (RuntimeException exception) {
                skipped.add(item == null || clean(item.id()) == null ? "UNKNOWN" : item.id().trim());
                continue;
            }
            var existing = announcements.findBySourceProviderAndExternalId("CONTEST_KOREA", item.id().trim());
            var announcement = existing.orElseGet(() -> Announcement.builder().build());
            announcement.syncContestKorea(item.id().trim(), clean(item.category()), item.title().trim(), item.host().trim(),
                    category.field(), period.start(), period.deadline(), clean(item.target()),
                    clean(item.participationRegion()), item.period().trim(), item.sourceUrl().trim(), clean(item.posterUrl()));
            announcements.save(announcement);
            if (existing.isEmpty()) created++; else updated++;
        }
        return new ImportResult(created, updated, List.copyOf(skipped));
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static boolean tooLong(String value) { return value != null && value.length() > 255; }
    private static boolean validUrl(String value) {
        if (clean(value) == null || value.trim().length() > 2000) return false;
        try {
            var uri = java.net.URI.create(value.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) { return false; }
    }
}
