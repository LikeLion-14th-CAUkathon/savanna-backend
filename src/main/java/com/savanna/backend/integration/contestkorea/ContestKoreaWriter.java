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
    private final SourceAudit audit;
    public record ImportResult(int created, int updated, int excluded, List<String> skippedIds, List<String> issues) {}

    @Transactional
    public ImportResult save(ContestKoreaCategory category, List<ContestKoreaItem> items) {
        if (category == null || items == null) throw new IllegalArgumentException("분야와 공고 목록이 필요합니다.");
        int created = 0, updated = 0, excluded = 0;
        var skipped = new ArrayList<String>();
        var issues = new ArrayList<String>();
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
            String oldCodes = announcement.getExternalCategoryCodes();
            announcement.syncContestKorea(item.id().trim(), clean(item.category()), item.title().trim(), item.host().trim(),
                    category.field(), period.start(), period.deadline(), clean(item.target()),
                    clean(item.participationRegion()), item.period().trim(), item.sourceUrl().trim(), clean(item.posterUrl()));
            var primary = ContestKoreaCategory.fromSource(item.category());
            announcement.recordCategory(category.code(), primary.map(ContestKoreaCategory::code).orElse(null));
            if(primary.isEmpty() && clean(item.category())!=null) announcement.flagUnsupportedApiCategory();
            announcements.save(announcement);
            if (announcement.getExternalCategoryCodes().contains("|")) {
                issues.add(item.id() + ":MULTIPLE_CATEGORIES:" + announcement.getExternalCategoryCodes());
                if (!announcement.getExternalCategoryCodes().equals(oldCodes)) audit.record(announcement, item.id(), "API", "MULTIPLE_CATEGORIES", announcement.getExternalCategoryCodes());
            }
            if (announcement.getClassificationIssue()!=null) issues.add(item.id()+":"+announcement.getClassificationIssue());
            if (!announcement.isAvailable()) excluded++;
            if (announcement.getCsvDeadline()!=null && (!announcement.getCsvDeadline().equals(period.deadline())
                    || !java.util.Objects.equals(announcement.getCsvStartDate(),period.start()))) {
                issues.add(item.id()+":DATE_CONFLICT");
                audit.record(announcement,item.id(),"API","DATE_CONFLICT",java.util.Map.of("apiStart",period.start().toString(),"apiDeadline",period.deadline().toString(),
                        "csvStart",String.valueOf(announcement.getCsvStartDate()),"csvDeadline",String.valueOf(announcement.getCsvDeadline()),"selected","CSV"));
            }
            if (existing.isEmpty()) created++; else updated++;
        }
        return new ImportResult(created, updated, excluded, List.copyOf(skipped), List.copyOf(issues));
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
