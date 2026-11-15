package com.savanna.backend.announcement;
import com.savanna.backend.domain.User;
import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.dto.request.TeamCreateRequestDto;
import com.savanna.backend.invitation.InvitationTokenProvider;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class TeamCreationInvitationAtomicityTest {
    @Autowired TeamService service;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TeamInvitationRepository invitations;
    @MockitoSpyBean InvitationTokenProvider tokens;
    @Test void invitationFailureRollsBackTeamAndMembership() {
        String id=UUID.randomUUID().toString();var user=new User(id+"@example.invalid",id);user.updateProfile("팀장","대학",EnrollmentStatus.ENROLLED);user.updateCharacter(1);users.saveAndFlush(user);
        long teamCount=teams.count(),memberCount=members.count(),invitationCount=invitations.count();
        try {
            doThrow(new IllegalStateException("test invitation failure")).when(tokens).createSeed();
            assertThrows(IllegalStateException.class,()->service.createTeam(user.getUserId(),new TeamCreateRequestDto("team","project","intro",null,null,null,null)));
            assertEquals(teamCount,teams.count());assertEquals(memberCount,members.count());assertEquals(invitationCount,invitations.count());
        } finally {users.deleteById(user.getUserId());}
    }
}
