package com.savanna.backend.integration.contestkorea;

import com.savanna.backend.service.AnnouncementAudiencePolicy;
import tools.jackson.databind.json.JsonMapper;
import java.sql.*;
import java.util.*;

/** Existing-data policy pass, before JPA startup. PREVIEW is a read-only connection; APPLY is atomic. */
public final class AnnouncementAudienceMigration {
    private AnnouncementAudienceMigration() {}
    public record Entry(long announcementId,String externalId,String title,String target,String decision,String reason) {}
    public record Result(String mode,int scanned,int exclusionCandidates,int changed,int released,int kept,List<Entry> exclusions,List<Entry> review) {}

    public static Result run(String url,String username,String password,String mode) throws SQLException {
        boolean apply=switch(mode.toUpperCase(Locale.ROOT)) {
            case "PREVIEW" -> false; case "APPLY" -> true;
            default -> throw new IllegalArgumentException("Audience mode must be PREVIEW or APPLY.");
        };
        if(!url.startsWith("jdbc:h2:file:")&&!url.startsWith("jdbc:h2:mem:"))throw new IllegalArgumentException("Local H2 only.");
        String connectionUrl=url+(url.contains("IFEXISTS=")?"":";IFEXISTS=TRUE")+(!apply?";ACCESS_MODE_DATA=r":"");
        var policy=new AnnouncementAudiencePolicy();var exclusions=new ArrayList<Entry>();var review=new ArrayList<Entry>();
        int scanned=0,changed=0,released=0,kept=0;
        try(var connection=DriverManager.getConnection(connectionUrl,username,password)) {
            connection.setAutoCommit(false);
            try {
                var rows=new ArrayList<Map<String,Object>>();
                try(var statement=connection.createStatement();var result=statement.executeQuery("select announcement_id,external_id,title,enrollment_requirement,exclude_reason,visibility from announcements order by announcement_id")) {
                    while(result.next()) {
                        var row=new HashMap<String,Object>();
                        for(String column:List.of("announcement_id","external_id","title","enrollment_requirement","exclude_reason","visibility"))row.put(column,result.getObject(column));
                        rows.add(row);
                    }
                }
                for(var row:rows) {
                    scanned++;long id=((Number)row.get("announcement_id")).longValue();
                    String externalId=(String)row.get("external_id"),title=(String)row.get("title"),target=(String)row.get("enrollment_requirement");
                    var decision=policy.assess(title,target);
                    var entry=new Entry(id,externalId,title,target,decision.action().name(),decision.reason());
                    if(decision.action()==AnnouncementAudiencePolicy.Action.REVIEW)review.add(entry);
                    String previous=(String)row.get("exclude_reason");
                    boolean alreadyMarked=previous!=null&&Arrays.asList(previous.split("\\n")).contains(AnnouncementAudiencePolicy.EXCLUDE_REASON);
                    boolean exclude=decision.action()==AnnouncementAudiencePolicy.Action.EXCLUDE;
                    if(exclude)exclusions.add(entry);else kept++;
                    if(!apply||exclude&&alreadyMarked||!exclude&&(!alreadyMarked||!decision.inclusiveAudience()))continue;
                    String reason;
                    if(exclude)reason=previous==null||previous.isBlank()?AnnouncementAudiencePolicy.EXCLUDE_REASON:previous+"\n"+AnnouncementAudiencePolicy.EXCLUDE_REASON;
                    else {
                        var remaining=Arrays.stream(previous.split("\\n")).filter(value->!value.equals(AnnouncementAudiencePolicy.EXCLUDE_REASON)).toList();
                        reason=remaining.isEmpty()?null:String.join("\n",remaining);
                    }
                    try(var update=connection.prepareStatement("update announcements set exclude_reason=? where announcement_id=?")) {
                        update.setString(1,reason);update.setLong(2,id);update.executeUpdate();
                    }
                    try(var audit=connection.prepareStatement("insert into announcement_source_events(announcement_id,external_id,source,event_type,details,recorded_at) values(?,?,'POLICY',?,?,CURRENT_TIMESTAMP)")) {
                        audit.setLong(1,id);audit.setString(2,externalId);
                        audit.setString(3,exclude?"AUDIENCE_EXCLUSION":"AUDIENCE_REASSESSED");
                        audit.setString(4,JsonMapper.builder().build().writeValueAsString(Map.of("policy",AnnouncementAudiencePolicy.VERSION,
                                "target",target,"reason",decision.reason(),"previousReason",String.valueOf(previous))));
                        audit.executeUpdate();
                    }
                    changed++;
                    if(!exclude)released++;
                }
                if(apply)connection.commit();else connection.rollback();
            }catch(SQLException|RuntimeException failure){try{connection.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;}
        }
        return new Result(apply?"APPLY":"PREVIEW",scanned,exclusions.size(),changed,released,kept,List.copyOf(exclusions),List.copyOf(review));
    }
}
