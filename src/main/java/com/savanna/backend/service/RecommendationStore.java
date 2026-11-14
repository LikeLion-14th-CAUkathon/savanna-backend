package com.savanna.backend.service;

import com.savanna.backend.domain.*;
import com.savanna.backend.dto.response.AnnouncementRecommendationResponse;
import com.savanna.backend.enums.*;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationStore {
    private final TeamAccessService access;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final AnnouncementRepository announcements;
    private final AnnouncementRecommendationRepository recommendations;
    private final AnnouncementEligibilityEvaluator evaluator;

    public record Context(Long teamId, String inputHash, RecommendationAiClient.TeamInput teamInput,
                          EligibilityTeamReader.TeamSnapshot teamSnapshot, List<RecommendationAiClient.Candidate> candidates) {}

    public Context prepare(Long teamId, Principal principal, boolean includeCandidates) {
        if (teamId == null || teamId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        access.requireMember(principal, teamId, false);
        var team = teams.findById(teamId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        var memberList = members.findAllByTeam_TeamId(teamId).stream()
                .sorted(Comparator.comparing(m -> m.getUser().getUserId())).toList();
        var statuses = memberList.stream().map(m -> m.getUser().getEnrollmentStatus()).toList();
        var snapshot = new EligibilityTeamReader.TeamSnapshot(teamId, statuses);
        var hashParts = new ArrayList<String>(Arrays.asList(team.getName(), team.getProjectName(), team.getProjectIntro(),
                team.getStack(), team.getGithubUrl(), team.getPresentationUrl()));
        memberList.forEach(m -> { hashParts.add(m.getUser().getUserId().toString()); hashParts.add(String.valueOf(m.getUser().getEnrollmentStatus())); });
        var now = now();
        List<RecommendationAiClient.Candidate> candidates = includeCandidates
                ? announcements.findByVisibilityTrueAndDeadlineGreaterThanEqualAndApplicationStartsAtLessThanEqualOrderByDeadlineAscAnnouncementIdAsc(
                        now.toLocalDate(), now, PageRequest.of(0, 200)).stream()
                    .map(a -> candidate(a, snapshot)).filter(c -> c.eligibility().status() != EligibilityStatus.MISMATCH)
                    .limit(20).toList()
                : List.of();
        return new Context(teamId, hash(hashParts), new RecommendationAiClient.TeamInput(team.getProjectName(),
                team.getProjectIntro(), team.getStack(), statuses), snapshot, candidates);
    }

    public List<AnnouncementRecommendationResponse> list(Long teamId, Principal principal) {
        return visible(prepare(teamId, principal, false));
    }

    private List<AnnouncementRecommendationResponse> visible(Context context) {
        var now = now();
        var result = new ArrayList<AnnouncementRecommendationResponse>();
        for (var recommendation : recommendations.findVisible(context.teamId(), now.toLocalDate())) {
            var announcement = recommendation.getAnnouncement();
            // A changed start date can make a previously recommended notice upcoming.
            if (announcement.getApplicationStartsAt() == null || announcement.getApplicationStartsAt().isAfter(now)) continue;
            var eligibility = evaluator.evaluate(announcement, context.teamId(), context.teamSnapshot());
            if (eligibility.status() == EligibilityStatus.MISMATCH) continue;
            result.add(new AnnouncementRecommendationResponse(announcement.getAnnouncementId(), announcement.getTitle(),
                    announcement.getHost(), announcement.getField(), announcement.getDeadline(), ReceptionStatus.OPEN,
                    recommendation.getScore(), recommendation.getReason(), eligibility, recommendation.getGeneratedAt(),
                    recommendation.getSource(), !recommendation.getInputHash().equals(context.inputHash())
                            || !recommendation.getAnnouncementHash().equals(candidateHash(candidate(announcement, context.teamSnapshot())))));
        }
        return List.copyOf(result);
    }

    @Transactional
    public List<AnnouncementRecommendationResponse> replace(Context prepared,
            List<RecommendationAiClient.Suggestion> suggestions, RecommendationSource source, Principal principal) {
        var team = teams.findForRecommendationUpdate(prepared.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        var current = prepare(prepared.teamId(), principal, false);
        if (!current.inputHash().equals(prepared.inputHash())) throw new BusinessException(ErrorCode.CONFLICT);
        var expected = prepared.candidates().stream().collect(Collectors.toMap(RecommendationAiClient.Candidate::announcementId, c -> c));
        var byId = announcements.findAllById(suggestions.stream().map(RecommendationAiClient.Suggestion::announcementId).toList())
                .stream().collect(Collectors.toMap(Announcement::getAnnouncementId, a -> a));
        var now = now();
        for (var suggestion : suggestions) {
            var announcement = byId.get(suggestion.announcementId());
            if (announcement == null || !announcement.isAvailable() || announcement.getDeadline().isBefore(now.toLocalDate())
                    || !candidate(announcement, current.teamSnapshot()).equals(expected.get(suggestion.announcementId()))) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
        }
        recommendations.deleteForTeam(prepared.teamId());
        recommendations.saveAll(suggestions.stream().map(s -> new AnnouncementRecommendation(team, byId.get(s.announcementId()),
                s.score(), s.reason().strip(), current.inputHash(), candidateHash(expected.get(s.announcementId())), source, now)).toList());
        return visible(current);
    }

    private RecommendationAiClient.Candidate candidate(Announcement a, EligibilityTeamReader.TeamSnapshot team) {
        return new RecommendationAiClient.Candidate(a.getAnnouncementId(), a.getTitle(), a.getField(), a.getTheme(),
                a.getEvaluationCriteria(), a.getSubmissionRequirements(), a.getEnrollmentRequirement(), a.getMinMembers(),
                a.getMaxMembers(), a.getParticipationRegion(), a.getApplicationStartsAt(), a.getDeadline(), evaluator.evaluate(a, team.teamId(), team));
    }

    private static String hash(List<String> parts) {
        var text = new StringBuilder();
        for (var part : parts) { if (part == null) text.append("-1:"); else text.append(part.length()).append(':').append(part); }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String candidateHash(RecommendationAiClient.Candidate candidate) { return hash(List.of(candidate.toString())); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }
}
