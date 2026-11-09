package com.savanna.backend.service;

import com.savanna.backend.domain.Expedition;
import com.savanna.backend.domain.ExpeditionStageHistory;
import com.savanna.backend.dto.request.ExpeditionScheduleRequest;
import com.savanna.backend.dto.request.ExpeditionStageRequest;
import com.savanna.backend.dto.response.ExpeditionResponse;
import com.savanna.backend.dto.response.ExpeditionStageHistoryResponse;
import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import com.savanna.backend.repository.ExpeditionRepository;
import com.savanna.backend.repository.ExpeditionStageHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.savanna.backend.dto.response.TeamExpeditionResponse;
import com.savanna.backend.enums.ProposalRecipientStatus;
import com.savanna.backend.repository.ProposalRecipientRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpeditionService {
    private final ExpeditionRepository expeditionRepository;
    private final ProposalRecipientRepository recipientRepository;
    private final ExpeditionStageHistoryRepository historyRepository;
    private final ObjectProvider<ExpeditionTeamAccess> teamAccess;

    public List<TeamExpeditionResponse> getTeamExpeditions(Long teamId, Principal principal) {
        if (teamId == null || teamId <= 0) invalid();
        authorize(principal, teamId, false);
        var expeditions = expeditionRepository.findTeamExpeditions(teamId);
        if (expeditions.isEmpty()) return List.of();
        Map<Long, Long> joined = recipientRepository
                .countDistinctUsersByTeamAndStatus(teamId, ProposalRecipientStatus.JOINED).stream()
                .collect(Collectors.toMap(
                        ProposalRecipientRepository.ExpeditionJoinedCount::getExpeditionId,
                        ProposalRecipientRepository.ExpeditionJoinedCount::getJoinedCount));
        return expeditions.stream().map(e -> TeamExpeditionResponse.from(e,
                joined.getOrDefault(e.getExpeditionId(), 0L))).toList();
    }
    public ExpeditionResponse getExpedition(Long id, Principal principal) {
        var e = find(id, false);
        authorize(principal, e.getTeamId(), false);
        return ExpeditionResponse.from(e);
    }

    @Transactional
    public ExpeditionResponse changeStage(Long id, ExpeditionStageRequest request, Principal principal) {
        if (request == null || request.stage() == null) invalid();
        var e = find(id, true);
        Long userId = authorize(principal, e.getTeamId(), true);
        var previous = e.getStage();
        if (previous != request.stage()) {
            e.changeStage(request.stage());
            historyRepository.save(new ExpeditionStageHistory(e, previous, e.getStage(), userId));
        }
        return ExpeditionResponse.from(e);
    }

    @Transactional
    public ExpeditionResponse updateSchedule(Long id, ExpeditionScheduleRequest request, Principal principal) {
        if (request == null) invalid();
        var e = find(id, true);
        authorize(principal, e.getTeamId(), true);
        e.updateSchedule(
                request.kickoffDate() == null ? e.getKickoffDate() : request.kickoffDate(),
                request.draftDate() == null ? e.getDraftDate() : request.draftDate(),
                request.reviewDate() == null ? e.getReviewDate() : request.reviewDate(),
                request.submitTargetDate() == null ? e.getSubmitTargetDate() : request.submitTargetDate());
        return ExpeditionResponse.from(e);
    }

    public Page<ExpeditionStageHistoryResponse> getStageHistories(
            Long id, int page, int size, Principal principal) {
        if (page < 0 || size < 1 || size > 100) invalid();
        var e = find(id, false);
        authorize(principal, e.getTeamId(), false);
        return historyRepository.findByExpedition_ExpeditionId(id, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("stageHistoryId"))))
                .map(ExpeditionStageHistoryResponse::from);
    }

    private Expedition find(Long id, boolean lock) {
        if (id == null || id <= 0) invalid();
        return (lock ? expeditionRepository.findForUpdate(id) : expeditionRepository.findByExpeditionId(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Long authorize(Principal principal, Long teamId, boolean write) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var access = teamAccess.getIfAvailable();
        if (access == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        Long userId = access.requireMember(principal, teamId, write);
        if (userId == null || userId <= 0) throw new BusinessException(ErrorCode.FORBIDDEN);
        return userId;
    }

    private static void invalid() {
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
