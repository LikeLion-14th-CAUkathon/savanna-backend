package com.savanna.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class ExpeditionTaskUpdateRequest {
    @Size(max = 1000)
    @Pattern(regexp = "(?s).*\\S.*", message = "내용은 공백일 수 없습니다.")
    private String title;
    private LocalDate dueDate;
    private LocalTime dueTime;
    private Boolean isDone;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean dueTimePresent;

    public void setTitle(String title) { this.title = title; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    @JsonSetter("dueTime")
    public void setDueTime(LocalTime dueTime) {
        this.dueTime = dueTime;
        this.dueTimePresent = true;
    }

    @JsonSetter("isDone")
    public void setIsDone(Boolean isDone) { this.isDone = isDone; }
}
