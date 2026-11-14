package com.savanna.backend.service;

import com.savanna.backend.domain.*;
import com.savanna.backend.dto.request.*;
import com.savanna.backend.dto.response.*;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpeditionChecklistService {
    private final ExpeditionRepository expeditionRepository;
    private final ExpeditionChecklistRepository checklistRepository;
    private final ObjectProvider<ExpeditionTeamAccess> teamAccess;

    public ExpeditionChecklistListResponse list(Long expeditionId, Principal principal) {
        var e = findExpedition(expeditionId, false);
        authorize(principal, e.getTeamId(), false);
        var items = checklistRepository
                .findByExpedition_ExpeditionIdOrderBySortOrderAscChecklistIdAsc(expeditionId)
                .stream().map(ExpeditionChecklistResponse::from).toList();
        return ExpeditionChecklistListResponse.from(items);
    }

    @Transactional
    public ExpeditionChecklistResponse create(Long expeditionId, ExpeditionChecklistRequest request,
                                               Principal principal) {
        if (request == null) invalid();
        // 원정 행을 잠가 동시 추가 시 기본 sortOrder 계산을 직렬화합니다.
        var e = findExpedition(expeditionId, true);
        authorize(principal, e.getTeamId(), true);
        Integer order = request.sortOrder();
        if (order == null) {
            Integer max = checklistRepository.findMaxSortOrder(expeditionId);
            if (max != null && max == Integer.MAX_VALUE) throw new BusinessException(ErrorCode.CONFLICT);
            order = max == null ? 0 : max + 1;
        }
        return ExpeditionChecklistResponse.from(checklistRepository.saveAndFlush(
                new ExpeditionChecklist(e, request.title(), order)));
    }

    @Transactional
    public ExpeditionChecklistResponse update(Long checklistId, ExpeditionChecklistUpdateRequest request,
                                               Principal principal) {
        if (request == null) invalid();
        var item = findItem(checklistId);
        authorize(principal, item.getExpedition().getTeamId(), true);
        item.update(request.title() == null ? item.getTitle() : request.title(),
                request.isChecked() == null ? item.isChecked() : request.isChecked(),
                request.sortOrder() == null ? item.getSortOrder() : request.sortOrder());
        return ExpeditionChecklistResponse.from(item);
    }

    @Transactional
    public void delete(Long checklistId, Principal principal) {
        var item = findItem(checklistId);
        authorize(principal, item.getExpedition().getTeamId(), true);
        checklistRepository.delete(item);
    }

    private Expedition findExpedition(Long id, boolean lock) {
        validateId(id);
        return (lock ? expeditionRepository.findForUpdate(id) : expeditionRepository.findById(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ExpeditionChecklist findItem(Long id) {
        validateId(id);
        return checklistRepository.findForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void authorize(Principal principal, Long teamId, boolean write) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var access = teamAccess.getIfAvailable();
        if (access == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        Long userId = access.requireMember(principal, teamId, write);
        if (userId == null || userId <= 0) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private static void validateId(Long id) { if (id == null || id <= 0) invalid(); }
    private static void invalid() { throw new BusinessException(ErrorCode.INVALID_REQUEST); }
}
