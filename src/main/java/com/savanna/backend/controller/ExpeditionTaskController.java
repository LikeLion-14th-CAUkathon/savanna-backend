package com.savanna.backend.controller;

import com.savanna.backend.dto.request.ExpeditionTaskRequest;
import com.savanna.backend.dto.request.ExpeditionTaskUpdateRequest;
import com.savanna.backend.dto.response.ExpeditionTaskResponse;
import com.savanna.backend.service.ExpeditionTaskService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExpeditionTaskController {
    private final ExpeditionTaskService taskService;

    @Operation(summary = "공지 목록 조회", description = "날짜·시간순, 같은 날짜의 시간 미정 공지는 마지막에 반환합니다.")
    @GetMapping("/expeditions/{expeditionId}/tasks")
    public List<ExpeditionTaskResponse> list(@PathVariable("expeditionId") Long expeditionId,
                                            Principal principal) {
        return taskService.list(expeditionId, principal);
    }

    @Operation(summary = "공지 추가", description = "dueTime은 선택이며 완료 여부는 false로 생성합니다.")
    @PostMapping("/expeditions/{expeditionId}/tasks")
    public ResponseEntity<ExpeditionTaskResponse> create(@PathVariable("expeditionId") Long expeditionId,
            @Valid @RequestBody ExpeditionTaskRequest request, Principal principal) {
        var task = taskService.create(expeditionId, request, principal);
        return ResponseEntity.created(URI.create("/api/v1/expedition-tasks/" + task.taskId()))
                .body(task);
    }

    @Operation(summary = "공지 수정", description = "생략 값은 유지합니다. dueTime:null은 시간 삭제, isDone:false는 완료 취소입니다.")
    @PatchMapping("/expedition-tasks/{taskId}")
    public ExpeditionTaskResponse update(@PathVariable("taskId") Long taskId,
            @Valid @RequestBody ExpeditionTaskUpdateRequest request, Principal principal) {
        return taskService.update(taskId, request, principal);
    }

    @Operation(summary = "공지 삭제")
    @DeleteMapping("/expedition-tasks/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable("taskId") Long taskId, Principal principal) {
        taskService.delete(taskId, principal);
        return ResponseEntity.noContent().build();
    }
}
