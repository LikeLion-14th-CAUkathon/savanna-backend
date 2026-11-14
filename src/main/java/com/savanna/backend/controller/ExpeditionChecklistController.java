package com.savanna.backend.controller;

import com.savanna.backend.dto.request.*;
import com.savanna.backend.dto.response.*;
import com.savanna.backend.service.ExpeditionChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExpeditionChecklistController {
    private final ExpeditionChecklistService service;

    @Operation(summary = "제출 체크리스트 조회", description = "sortOrder순 목록과 완료/전체 진행도를 반환합니다.")
    @GetMapping("/expeditions/{expeditionId}/checklists")
    public ExpeditionChecklistListResponse list(@PathVariable("expeditionId") Long id, Principal principal) {
        return service.list(id, principal);
    }

    @Operation(summary = "제출 체크리스트 항목 추가", description = "sortOrder 생략 시 마지막에 추가합니다.")
    @PostMapping("/expeditions/{expeditionId}/checklists")
    public ResponseEntity<ExpeditionChecklistResponse> create(@PathVariable("expeditionId") Long id,
            @Valid @RequestBody ExpeditionChecklistRequest request, Principal principal) {
        var item = service.create(id, request, principal);
        return ResponseEntity.created(URI.create("/api/v1/expedition-checklists/" + item.checklistId())).body(item);
    }

    @Operation(summary = "제출 체크리스트 체크/수정", description = "isChecked:true로 체크, false로 해제합니다. 생략 또는 null 필드는 유지합니다.")
    @PatchMapping("/expedition-checklists/{checklistId}")
    public ExpeditionChecklistResponse update(@PathVariable("checklistId") Long id,
            @Valid @RequestBody ExpeditionChecklistUpdateRequest request, Principal principal) {
        return service.update(id, request, principal);
    }

    @Operation(summary = "제출 체크리스트 항목 삭제")
    @DeleteMapping("/expedition-checklists/{checklistId}")
    public ResponseEntity<Void> delete(@PathVariable("checklistId") Long id, Principal principal) {
        service.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
