package com.savanna.backend.service;

import com.savanna.backend.domain.User;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class TeamMembershipPolicy {
    private final UserRepository users;
    private final TeamMemberRepository members;

    // Always acquire this user lock before any recipient/team lock in membership writers.
    public User lockUser(Long userId) {
        if(userId==null || userId<=0)throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return users.findForTeamMembership(userId)
                .orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED));
    }
    public void requireNoTeam(Long userId) { requireOnlyTeam(userId,null); }
    public void requireOnlyTeam(Long userId,Long targetTeamId) {
        var existing=members.findAllByUser_UserId(userId);
        if(existing.stream().anyMatch(m -> targetTeamId==null || !targetTeamId.equals(m.getTeam().getTeamId())))
            throw new BusinessException(ErrorCode.ALREADY_IN_TEAM);
    }
}
