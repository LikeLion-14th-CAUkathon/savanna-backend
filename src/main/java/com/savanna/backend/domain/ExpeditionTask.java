package com.savanna.backend.domain;

import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "expedition_tasks", indexes = @Index(
        name = "idx_expedition_tasks_expedition_due", columnList = "expedition_id,due_date,due_time"))
public class ExpeditionTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expedition_id", nullable = false, updatable = false)
    private Expedition expedition;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(nullable = false)
    private boolean isDone;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ExpeditionTask(Expedition expedition, String title, LocalDate dueDate, LocalTime dueTime) {
        if (expedition == null) invalid();
        validate(title, dueDate);
        this.expedition = expedition;
        this.title = title.trim();
        this.dueDate = dueDate;
        this.dueTime = dueTime;
    }

    public void update(String title, LocalDate dueDate, LocalTime dueTime, boolean done) {
        validate(title, dueDate);
        this.title = title.trim();
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.isDone = done;
    }

    private static void validate(String title, LocalDate dueDate) {
        if (title == null || title.isBlank() || title.length() > 1000 || dueDate == null) invalid();
    }

    private static void invalid() { throw new BusinessException(ErrorCode.INVALID_REQUEST); }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        this.updatedAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
