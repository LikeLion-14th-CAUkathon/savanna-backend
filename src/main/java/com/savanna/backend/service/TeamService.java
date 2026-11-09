package com.savanna.backend.service;

import com.savanna.backend.domain.Team;
import com.savanna.backend.domain.TeamMember;
import com.savanna.backend.domain.User;
import com.savanna.backend.dto.request.TeamCreateRequestDto;
import com.savanna.backend.dto.response.TeamCreateResponseDto;
import com.savanna.backend.enums.TeamRole;
import com.savanna.backend.repository.TeamMemberRepository;
import com.savanna.backend.repository.TeamRepository;
import com.savanna.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.savanna.backend.dto.response.TeamListResponseDto;
import com.savanna.backend.dto.response.TeamDetailResponseDto;
import com.savanna.backend.dto.request.TeamUpdateRequestDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamCreateResponseDto createTeam(
            Long userId,
            TeamCreateRequestDto request
    ) {

        // 1. 로그인 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 2. 온보딩 완료 여부 확인
        if (!user.isOnboardingCompleted()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "온보딩을 완료해야 팀을 생성할 수 있습니다."
            );
        }

        // 3. 팀 생성
        Team team = new Team(
                request.name().trim(),
                request.projectName().trim(),
                request.projectIntro().trim(),
                request.githubUrl(),
                request.presentationUrl(),
                request.stack()
        );

        teamRepository.save(team);

        // 4. 생성자를 LEADER로 등록
        TeamMember teamMember = new TeamMember(
                user,
                team,
                TeamRole.LEADER
        );

        teamMemberRepository.save(teamMember);

        // 5. 결과 반환
        return TeamCreateResponseDto.from(
                team,
                TeamRole.LEADER
        );
    }

    @Transactional(readOnly = true)
    public List<TeamListResponseDto> getMyTeams(Long userId) {

        return teamMemberRepository
                .findAllByUser_UserId(userId)
                .stream()
                .map(TeamListResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamDetailResponseDto getTeamDetail(
            Long userId,
            Long teamId
    ) {
        // 1. 팀 존재 여부 확인
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "팀을 찾을 수 없습니다."
                        )
                );

        // 2. 현재 사용자의 팀 소속 여부 확인
        boolean isMember = teamMemberRepository
                .existsByUser_UserIdAndTeam_TeamId(
                        userId,
                        teamId
                );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 팀에 접근할 권한이 없습니다."
            );
        }

        // 3. 팀원 목록 조회
        List<TeamMember> members = teamMemberRepository
                .findAllByTeam_TeamId(teamId);

        // 4. 상세 정보 반환
        return TeamDetailResponseDto.from(team, members);
    }

    @Transactional
    public TeamDetailResponseDto updateTeam(
            Long userId,
            Long teamId,
            TeamUpdateRequestDto request
    ) {
        // 1. 팀 조회
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "팀을 찾을 수 없습니다."
                        )
                );

        // 2. 사용자 소속 및 역할 확인
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
                    "팀장만 팀 정보를 수정할 수 있습니다."
            );
        }

        // 3. 팀 정보 수정
        try {
            team.update(
                    request.name(),
                    request.projectName(),
                    request.projectIntro(),
                    request.githubUrl(),
                    request.presentationUrl(),
                    request.stack()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }

        // 4. 수정된 팀원 목록 조회
        var members = teamMemberRepository
                .findAllByTeam_TeamId(teamId);

        // 5. 수정 결과 반환
        return TeamDetailResponseDto.from(team, members);
    }

}
