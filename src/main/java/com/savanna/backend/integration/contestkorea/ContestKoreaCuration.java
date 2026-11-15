package com.savanna.backend.integration.contestkorea;

import tools.jackson.databind.json.JsonMapper;
import java.sql.*;
import java.util.*;

/** Offline maintenance: exact user-approved IDs only; no JPA, API calls, schema changes or cascading expedition deletion. */
public final class ContestKoreaCuration {
    private ContestKoreaCuration() {}
    public record Removed(long announcementId,String externalId,String title) {}
    public record Result(String mode,int csvRows,int matchedCsvRows,int targetCorrectionCandidates,int correctedTargets,
                         List<String> unmatchedCsvIds,List<Removed> deletionCandidates,Map<String,Long> blockingReferences,
                         int deletedAnnouncements,int deletedRecommendations,int deletedSourceEvents,long remainingRemovedAnnouncements) {}
    private static final JsonMapper JSON=JsonMapper.builder().build();

    public static Result run(String url,String username,String password,List<Map<String,String>> rows,String label,String mode) throws SQLException {
        boolean apply=switch(mode.toUpperCase(Locale.ROOT)){case "PREVIEW"->false;case "APPLY"->true;default->throw new IllegalArgumentException("Curation mode must be PREVIEW or APPLY");};
        if(!url.startsWith("jdbc:h2:file:")&&!url.startsWith("jdbc:h2:mem:"))throw new IllegalArgumentException("Local H2 only");
        String connectionUrl=url+(url.contains("IFEXISTS=")?"":";IFEXISTS=TRUE")+(!apply?";ACCESS_MODE_DATA=r":"");
        int matched=0,candidates=0,corrected=0,deleted=0,recommendations=0,events=0;
        var unmatched=new ArrayList<String>();var removed=new ArrayList<Removed>();var blocked=new LinkedHashMap<String,Long>();
        String ids=ContestKoreaRemovalRegistry.REMOVED.keySet().stream().sorted().map(id->"'"+id+"'").collect(java.util.stream.Collectors.joining(","));
        String predicate="source_provider='CONTEST_KOREA' and external_id in ("+ids+")";
        try(var c=DriverManager.getConnection(connectionUrl,username,password)) {
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement();var r=s.executeQuery("select announcement_id,external_id,title from announcements where "+predicate+" order by external_id")) {
                    while(r.next())removed.add(new Removed(r.getLong(1),r.getString(2),r.getString(3)));
                }
                String localIds=removed.isEmpty()?"-1":removed.stream().map(r->Long.toString(r.announcementId())).collect(java.util.stream.Collectors.joining(","));
                // Refuse to delete any user-created expedition/proposal/task chain or unknown referenced data.
                try(var fks=c.getMetaData().getExportedKeys(null,"PUBLIC","ANNOUNCEMENTS")) {
                    while(fks.next()){
                        String table=fks.getString("FKTABLE_NAME"),column=fks.getString("FKCOLUMN_NAME");
                        if("ANNOUNCEMENT_RECOMMENDATIONS".equals(table))continue;
                        if(!table.matches("[A-Z0-9_]+")||!column.matches("[A-Z0-9_]+"))throw new SQLException("Unsupported reference identifier");
                        long count=count(c,"select count(*) from \""+table+"\" where \""+column+"\" in ("+localIds+")");
                        if(count>0)blocked.put(table+"."+column,count);
                    }
                }
                if(apply&&!blocked.isEmpty())throw new IllegalStateException("PURGE_BLOCKED_REFERENCES "+blocked+"; no records changed");
                var seen=new HashSet<String>();int line=1;
                for(var row:rows){
                    line++;String id=row.get("api_id");
                    if(id==null||id.isBlank()||!seen.add(id))throw new IllegalArgumentException("CSV missing or duplicate api_id");
                    if(ContestKoreaRemovalRegistry.contains(id))continue;
                    String target=row.get("target_text");if(target==null||target.isBlank())continue;
                    try(var lookup=c.prepareStatement("select announcement_id,enrollment_requirement,csv_metadata from announcements where source_provider='CONTEST_KOREA' and external_id=?")) {
                        lookup.setString(1,id);
                        try(var r=lookup.executeQuery()) {
                            if(!r.next()){unmatched.add(id);continue;}matched++;
                            String previous=r.getString(2);target=target.strip();if(Objects.equals(previous,target))continue;candidates++;
                            if(!apply)continue;
                            long announcementId=r.getLong(1);
                            String metadata=correctedMetadata(r.getString(3),row,label,line);
                            try(var update=c.prepareStatement("update announcements set enrollment_requirement=?,csv_metadata=? where announcement_id=?")) {
                                update.setString(1,target);update.setString(2,metadata);update.setLong(3,announcementId);update.executeUpdate();
                            }
                            try(var audit=c.prepareStatement("insert into announcement_source_events(announcement_id,external_id,source,event_type,details,recorded_at) values(?,?,'CSV','TARGET_CORRECTION',?,CURRENT_TIMESTAMP)")) {
                                audit.setLong(1,announcementId);audit.setString(2,id);
                                var details=new LinkedHashMap<String,Object>();details.put("source",label);details.put("row",line);details.put("previous",previous);details.put("target_raw",row.get("target_raw"));details.put("target_text",target);
                                audit.setString(3,JSON.writeValueAsString(details));audit.executeUpdate();
                            }
                            corrected++;
                        }
                    }
                }
                if(apply){
                    if(tableExists(c,"ANNOUNCEMENT_RECOMMENDATIONS"))recommendations=execute(c,"delete from announcement_recommendations where announcement_id in ("+localIds+")");
                    if(tableExists(c,"ANNOUNCEMENT_SOURCE_EVENTS"))events=execute(c,"delete from announcement_source_events where announcement_id in ("+localIds+")");
                    deleted=execute(c,"delete from announcements where "+predicate);
                }
                long remaining=count(c,"select count(*) from announcements where "+predicate);
                if(apply&&remaining!=0)throw new IllegalStateException("Removed announcements remain; transaction aborted");
                if(apply)c.commit();else c.rollback();
                return new Result(apply?"APPLY":"PREVIEW",rows.size(),matched,candidates,corrected,List.copyOf(unmatched),List.copyOf(removed),Map.copyOf(blocked),deleted,recommendations,events,remaining);
            }catch(SQLException|RuntimeException ex){try{c.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}throw ex;}
        }
    }
    @SuppressWarnings("unchecked")
    private static String correctedMetadata(String previous,Map<String,String> row,String source,int line) {
        Map<String,Object> data=previous==null||previous.isBlank()?new LinkedHashMap<>():new LinkedHashMap<>(JSON.readValue(previous,Map.class));
        Map<String,Object> values=data.get("values") instanceof Map<?,?> existing?new LinkedHashMap<>((Map<String,Object>)existing):new LinkedHashMap<>();
        values.put("target_text",row.get("target_text"));values.put("target_raw",row.get("target_raw"));data.put("values",values);
        data.put("targetCorrection",Map.of("source",source,"row",line,"selectedColumn","target_text"));return JSON.writeValueAsString(data);
    }
    private static long count(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private static int execute(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){return s.executeUpdate(sql);}}
    private static boolean tableExists(Connection c,String table)throws SQLException{try(var r=c.getMetaData().getTables(null,"PUBLIC",table,null)){return r.next();}}
}
