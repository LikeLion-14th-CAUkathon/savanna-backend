package com.savanna.backend.domain;

import com.savanna.backend.error.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "expedition_checklists", indexes = @Index(
        name = "idx_checklist_expedition_order", columnList = "expedition_id,sort_order"))
public class ExpeditionChecklist {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long checklistId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expedition_id", nullable = false, updatable = false)
    private Expedition expedition;
    @Column(nullable = false, length = 1000)
    private String title;
    @Column(nullable = false)
    private boolean isChecked;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public ExpeditionChecklist(Expedition expedition, String title, int sortOrder) {
        if (expedition == null) invalid();
        validate(title, sortOrder);
        this.expedition = expedition;
        this.title = title.trim();
        this.sortOrder = sortOrder;
    }

    public void update(String title, boolean checked, int sortOrder) {
        validate(title, sortOrder);
        this.title = title.trim();
        this.isChecked = checked;
        this.sortOrder = sortOrder;
    }

    private static void validate(String title, int sortOrder) {
        if (title == null || title.isBlank() || title.length() > 1000 || sortOrder < 0) invalid();
    }
    private static void invalid() { throw new BusinessException(ErrorCode.INVALID_REQUEST); }
}
