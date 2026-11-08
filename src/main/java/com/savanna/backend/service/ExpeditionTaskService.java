package com.savanna.backend.service;

import com.savanna.backend.domain.*;
import com.savanna.backend.dto.request.*;
import com.savanna.backend.dto.response.ExpeditionTaskResponse;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpeditionTaskService {
    private final ExpeditionRepository expeditionRepository;
    private final ExpeditionTaskRepository taskRepository;
    private final ObjectProvider<ExpeditionTeamAccess> teamAccess;

    public List<ExpeditionTaskResponse> list(Long expeditionId, Principal principal) {
        var expedition = findExpedition(expeditionId);
        authorize(principal, expedition.getTeamId(), false);
        return taskRepository.findOrderedByExpeditionId(expeditionId).stream()
                .map(ExpeditionTaskResponse::from).toList();
    }

    @Transactional
    public ExpeditionTaskResponse create(Long expeditionId, ExpeditionTaskRequest request, Principal principal) {
        if (request == null) invalid();
        var expedition = findExpedition(expeditionId);
        authorize(principal, expedition.getTeamId(), true);
        return ExpeditionTaskResponse.from(taskRepository.saveAndFlush(new ExpeditionTask(
                expedition, request.title(), request.dueDate(), request.dueTime())));
    }

    @Transactional
    public ExpeditionTaskResponse update(Long taskId, ExpeditionTaskUpdateRequest request, Principal principal) {
        if (request == null) invalid();
        var task = findTask(taskId);
        authorize(principal, task.getExpedition().getTeamId(), true);
        task.update(
                request.getTitle() == null ? task.getTitle() : request.getTitle(),
                request.getDueDate() == null ? task.getDueDate() : request.getDueDate(),
                request.isDueTimePresent() ? request.getDueTime() : task.getDueTime(),
                request.getIsDone() == null ? task.isDone() : request.getIsDone());
        taskRepository.flush();
        return ExpeditionTaskResponse.from(task);
    }

    @Transactional
    public void delete(Long taskId, Principal principal) {
        var task = findTask(taskId);
        authorize(principal, task.getExpedition().getTeamId(), true);
        taskRepository.delete(task);
    }

    private Expedition findExpedition(Long id) {
        validateId(id);
        return expeditionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ExpeditionTask findTask(Long id) {
        validateId(id);
        return taskRepository.findForUpdate(id)
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
