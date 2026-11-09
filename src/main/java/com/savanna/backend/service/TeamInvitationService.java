package com.savanna.backend.service;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamInvitation;
import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.domain.User;
import com.savanna.backend.dto.response.TeamInvitationCreateResponseDto;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.repository.TeamInvitationRepository;
import com.savanna.backend.repository.TeamMemberRepository;
import com.savanna.backend.repository.TeamRepository;
import com.savanna.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.savanna.backend.dto.response.TeamInvitationPreviewResponseDto;
import com.savanna.backend.dto.response.TeamInvitationAcceptResponseDto;
import com.savanna.backend.dto.response.TeamInvitationListResponseDto;
import java.util.List;
import java.util.Comparator;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class TeamInvitationService {

    private final TeamInvitationRepository invitationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    @Transactional
    public TeamInvitationCreateResponseDto createInvitation(
            Long userId,
            Long teamId
    ) {

        // 1. 팀 조회
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "팀을 찾을 수 없습니다."
                        )
                );

        // 2. 팀장 여부 확인
        TeamMember member = teamMemberRepository
                .findByUser_UserIdAndTeam_TeamId(userId, teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "해당 팀에 접근할 권한이 없습니다."
                        )
                );

        if (member.getRole() != TeamRole.LEADER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "팀장만 초대 링크를 생성할 수 있습니다."
            );
        }

        // 3. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 4. 안전한 초대 토큰 생성
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // 5. 토큰 해시 생성
        String tokenHash = hashToken(token);

        // 6. 초대 정보 저장
        TeamInvitation invitation = new TeamInvitation(
                team,
                user,
                tokenHash
        );

        invitationRepository.save(invitation);

        // 7. 생성 결과 반환
        return new TeamInvitationCreateResponseDto(
                invitation.getInvitationId(),
                team.getTeamId(),
                token,
                invitation.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public TeamInvitationPreviewResponseDto previewInvitation(
            String token
    ) {
        // 1. 토큰 해시 생성
        String tokenHash = hashToken(token);

        // 2. 초대 정보 조회
        TeamInvitation invitation = invitationRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "초대 링크를 찾을 수 없습니다."
                        )
                );

        // 3. 만료·폐기 여부 확인
        if (!invitation.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "만료되었거나 폐기된 초대 링크입니다."
            );
        }

        // 4. 팀 정보 반환
        return TeamInvitationPreviewResponseDto.from(
                invitation
        );
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashed =
                    digest.digest(token.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                    ));

            return HexFormat.of().formatHex(hashed);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.", e
            );
        }
    }

    @Transactional
    public TeamInvitationAcceptResponseDto acceptInvitation(
            Long userId,
            String token
    ) {
        // 1. 초대 토큰 해시 생성
        String tokenHash = hashToken(token);

        // 2. 초대 정보 조회
        TeamInvitation invitation = invitationRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "초대 링크를 찾을 수 없습니다."
                        )
                );

        // 3. 초대 링크 유효성 검사
        if (!invitation.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "만료되었거나 폐기된 초대 링크입니다."
            );
        }

        // 4. 로그인 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 5. 온보딩 완료 여부 확인
        if (!user.isOnboardingCompleted()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "온보딩을 완료해야 팀에 합류할 수 있습니다."
            );
        }

        // 6. 초대된 팀 조회
        Team team = invitation.getTeam();

        // 7. 중복 가입 여부 확인
        boolean alreadyJoined = teamMemberRepository
                .existsByUser_UserIdAndTeam_TeamId(
                        userId,
                        team.getTeamId()
                );

        if (alreadyJoined) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 가입한 팀입니다."
            );
        }

        // 8. 팀원 등록
        TeamMember newMember = new TeamMember(
                user,
                team,
                TeamRole.MEMBER
        );

        teamMemberRepository.save(newMember);

        // 9. 합류 결과 반환
        return TeamInvitationAcceptResponseDto.from(team);
    }

    private void validateLeader(Long userId, Long teamId) {

        TeamMember member = teamMemberRepository
                .findByUser_UserIdAndTeam_TeamId(userId, teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "해당 팀에 접근할 권한이 없습니다."
                        )
                );

        if (member.getRole() != TeamRole.LEADER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "팀장만 초대 링크를 관리할 수 있습니다."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TeamInvitationListResponseDto> getInvitations(
            Long userId,
            Long teamId
    ) {
        // 1. 팀 존재 여부 확인
        if (!teamRepository.existsById(teamId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "팀을 찾을 수 없습니다."
            );
        }

        // 2. 팀장 권한 확인
        validateLeader(userId, teamId);

        // 3. 초대 목록 조회
        return invitationRepository
                .findAllByTeam_TeamId(teamId)
                .stream()
                .sorted(
                        Comparator.comparing(
                                TeamInvitation::getCreatedAt
                        ).reversed()
                )
                .map(TeamInvitationListResponseDto::from)
                .toList();
    }

    @Transactional
    public void revokeInvitation(
            Long userId,
            Long teamId,
            Long invitationId
    ) {
        // 1. 팀 존재 여부 확인
        if (!teamRepository.existsById(teamId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "팀을 찾을 수 없습니다."
            );
        }

        // 2. 팀장 권한 확인
        validateLeader(userId, teamId);

        // 3. 초대 정보 조회
        TeamInvitation invitation = invitationRepository
                .findById(invitationId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "초대 링크를 찾을 수 없습니다."
                        )
                );

        // 4. 요청한 팀의 초대 링크인지 확인
        if (!invitation.getTeam().getTeamId().equals(teamId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "초대 링크를 찾을 수 없습니다."
            );
        }

        // 5. 링크 폐기
        invitation.revoke();
    }

}
