package com.savanna.backend.service;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.integration.contestkorea.SourceAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service @RequiredArgsConstructor
public class AnnouncementAudienceService {
    private final AnnouncementAudiencePolicy policy;
    private final SourceAudit audit;

    /** Called inside the creation/import transaction after the entity has an ID. Other policies/manual visibility are preserved. */
    public AnnouncementAudiencePolicy.Decision apply(Announcement announcement,String source) {
        var decision=policy.assess(announcement.getTitle(),announcement.getEnrollmentRequirement());
        String previous=announcement.getExcludeReason();
        if(decision.action()==AnnouncementAudiencePolicy.Action.EXCLUDE) {
            if(announcement.addPolicyExclusion(AnnouncementAudiencePolicy.EXCLUDE_REASON))audit.record(announcement,announcement.getExternalId(),source,"AUDIENCE_EXCLUSION",Map.of(
                    "policy",AnnouncementAudiencePolicy.VERSION,"reason",decision.reason(),
                    "target",announcement.getEnrollmentRequirement(),"previousReason",String.valueOf(previous)));
        }else if(decision.inclusiveAudience()&&announcement.removePolicyExclusion(AnnouncementAudiencePolicy.EXCLUDE_REASON)) {
            audit.record(announcement,announcement.getExternalId(),source,"AUDIENCE_REASSESSED",Map.of(
                    "policy",AnnouncementAudiencePolicy.VERSION,"reason",decision.reason(),
                    "target",announcement.getEnrollmentRequirement(),"previousReason",String.valueOf(previous)));
        }
        return decision;
    }
}
