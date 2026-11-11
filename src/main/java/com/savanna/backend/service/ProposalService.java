package com.savanna.backend.service;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.domain.Expedition;
import com.savanna.backend.domain.Proposal;
import com.savanna.backend.domain.ProposalRecipient;
import com.savanna.backend.dto.request.ProposalCreateRequest;
import com.savanna.backend.dto.response.ProposalCreateResponse;
import com.savanna.backend.error.BusinessException;
import com.savanna.backend.error.ErrorCode;
import com.savanna.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final AnnouncementRepository announcementRepository;
    private final ExpeditionRepository expeditionRepository;
    private final ProposalRepository proposalRepository;
    private final ProposalRecipientRepository recipientRepository;
    private final UserRepository userRepository;

    private final ObjectProvider<ProposalTeamAccess> teamAccess;

    @Transactional
    public ProposalCreateResponse createProposal(
            Long announcementId,
            ProposalCreateRequest request,
            Principal principal
    ) {
        // 1. 요청 검증
        if (announcementId == null || announcementId <= 0
                || request == null
                || request.message() == null
                || request.message().isBlank()
                || request.message().length() > 2000) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 2. 로그인·팀 소속 확인
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        ProposalTeamAccess access = teamAccess.getIfAvailable();

        if (access == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        var context = access.requireTeam(principal, request.teamId());

        if (context == null
                || context.teamId() == null
                || context.teamId() <= 0
                || (request.teamId() != null && !request.teamId().equals(context.teamId()))
                || context.senderId() == null
                || context.senderId() <= 0
                || context.recipientIds() == null
                || context.recipientIds().stream()
                .anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        var sender = userRepository.findById(context.senderId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.UNAUTHORIZED));

        // 3. 공고 조회·잠금
        Announcement announcement = announcementRepository
                .findForProposal(announcementId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!announcement.isAvailable()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 4. 이미 제안한 팀·공고인지 확인
        if (expeditionRepository
                .existsByTeamIdAndAnnouncement_AnnouncementId(
                        context.teamId(),
                        announcementId
                )) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        // 5. 접수 기간 확인
        LocalDateTime now =
                LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        if (announcement.getApplicationStartsAt() == null
                || announcement.getApplicationStartsAt().isAfter(now)
                || announcement.getDeadline() == null
                || announcement.getDeadline().isBefore(now.toLocalDate())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 6. 수신자 조회: 중복 제거·제안자 제외
        var recipientIds = new HashSet<>(context.recipientIds());
        recipientIds.remove(context.senderId());

        var recipients = userRepository.findAllById(recipientIds);

        if (recipients.size() != recipientIds.size()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        // 7. 원정 생성: 기본 단계 KICKOFF
        Expedition expedition = expeditionRepository.save(
                new Expedition(context.teamId(), announcement)
        );

        // 8. 제안 저장
        Proposal proposal = proposalRepository.save(
                new Proposal(
                        expedition,
                        sender,
                        request.message().strip()
                )
        );

        // 9. 수신자 저장: 기본 상태 PENDING
        var recipientRecords = recipients.stream()
                .map(user -> new ProposalRecipient(proposal, user))
                .toList();

        recipientRepository.saveAll(recipientRecords);

        return ProposalCreateResponse.from(proposal);
    }
}
