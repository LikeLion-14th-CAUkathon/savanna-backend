package com.savanna.backend.announcement;

import com.savanna.backend.domain.*;
import com.savanna.backend.enums.EnrollmentStatus;
import com.savanna.backend.dto.request.TeamCreateRequestDto;
import com.savanna.backend.error.*;
import com.savanna.backend.repository.*;
import com.savanna.backend.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SingleTeamMembershipConcurrencyTest {
    @Autowired TeamService service;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TeamInvitationRepository invitations;
    @Autowired PlatformTransactionManager transactions;
    @Test void simultaneousCreatesProduceExactlyOneTeamAndOneMembership() throws Exception {
        var tx=new TransactionTemplate(transactions);String id=UUID.randomUUID().toString();
        Long userId=tx.execute(s->{var u=new User(id+"@test.invalid",id);u.updateProfile("name","uni",EnrollmentStatus.ENROLLED);u.updateCharacter(1);return users.saveAndFlush(u).getUserId();});
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> create=()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));try{service.createTeam(userId,new TeamCreateRequestDto("team","project","intro",null,null,null,null));return true;}catch(BusinessException ex){assertEquals(ErrorCode.ALREADY_IN_TEAM,ex.getErrorCode());return false;}};
        try {
            var a=pool.submit(create);var b=pool.submit(create);assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            tx.executeWithoutResult(s->assertEquals(1,members.findAllByUser_UserId(userId).size()));
        } finally {
            start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));
            tx.executeWithoutResult(s->{var existing=members.findAllByUser_UserId(userId);var teamIds=existing.stream().map(m->m.getTeam().getTeamId()).toList();for(Long teamId:teamIds)invitations.deleteAll(invitations.findAllByTeam_TeamId(teamId));invitations.flush();members.deleteAll(existing);members.flush();teams.deleteAllById(teamIds);users.deleteById(userId);});
        }
    }
}
