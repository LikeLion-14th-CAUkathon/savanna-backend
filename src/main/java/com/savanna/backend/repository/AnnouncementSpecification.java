package com.savanna.backend.repository;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.enums.AnnouncementField;
import com.savanna.backend.enums.ReceptionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Locale;

public final class AnnouncementSpecification {

    private AnnouncementSpecification() {
    }

    public static Specification<Announcement> search(
            String keyword,
            AnnouncementField field,
            ReceptionStatus status,
            LocalDateTime now
    ) {
        return (root, query, cb) -> {
            // 공개 공고만 조회
            var predicate = cb.isTrue(root.get("visibility"));

            // 제목 또는 주최기관 검색
            if (keyword != null && !keyword.isBlank()) {
                String escaped = keyword.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");

                String pattern = "%" + escaped + "%";

                var keywordPredicate = cb.or(
                        cb.like(
                                cb.lower(root.<String>get("title")),
                                pattern,
                                '\\'
                        ),
                        cb.like(
                                cb.lower(root.<String>get("host")),
                                pattern,
                                '\\'
                        )
                );

                predicate = cb.and(predicate, keywordPredicate);
            }

            // 분야 필터
            if (field != null) {
                predicate = cb.and(
                        predicate,
                        cb.equal(root.get("field"), field)
                );
            }

            // 접수 상태 미선택 시 마감 공고 제외
            if (status == null) {
                predicate = cb.and(
                        predicate,
                        cb.greaterThanOrEqualTo(
                                root.<LocalDate>get("deadline"),
                                now.toLocalDate()
                        )
                );

            } else {
                var statusPredicate = switch (status) {
                    case UPCOMING -> cb.and(
                            cb.greaterThan(
                                    root.<LocalDateTime>get(
                                            "applicationStartsAt"
                                    ),
                                    now
                            ),
                            cb.greaterThanOrEqualTo(
                                    root.<LocalDate>get("deadline"),
                                    now.toLocalDate()
                            )
                    );

                    case OPEN -> cb.and(
                            cb.lessThanOrEqualTo(
                                    root.<LocalDateTime>get("applicationStartsAt"), now
                            ),
                            cb.greaterThanOrEqualTo(
                                    root.<LocalDate>get("deadline"),
                                    now.toLocalDate()
                            )
                    );

                    case CLOSED -> cb.lessThan(
                            root.<LocalDate>get("deadline"),
                            now.toLocalDate()
                    );
                };

                predicate = cb.and(predicate, statusPredicate);
            }

            return predicate;
        };
    }
}
