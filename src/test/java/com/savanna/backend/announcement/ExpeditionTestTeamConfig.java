package com.savanna.backend.announcement;

import com.savanna.backend.error.*;
import com.savanna.backend.service.ExpeditionTeamAccess;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class ExpeditionTestTeamConfig {
    @Bean @org.springframework.context.annotation.Primary ExpeditionTeamAccess testTeamAccess() {
        return (principal, teamId, write) -> {
            if (!"member".equals(principal.getName()) || !Long.valueOf(1).equals(teamId))
                throw new BusinessException(ErrorCode.FORBIDDEN);
            return 7L;
        };
    }
}
