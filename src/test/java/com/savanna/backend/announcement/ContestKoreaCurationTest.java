package com.savanna.backend.announcement;
import com.savanna.backend.integration.contestkorea.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContestKoreaCurationTest {
    String database()throws Exception {
        String url="jdbc:h2:mem:curation-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
            s.execute("create table announcements(announcement_id bigint primary key,source_provider varchar,external_id varchar,title varchar,enrollment_requirement varchar,csv_metadata varchar,theme varchar)");
            s.execute("create table announcement_recommendations(id bigint,announcement_id bigint references announcements(announcement_id))");
            s.execute("create table expeditions(id bigint,announcement_id bigint references announcements(announcement_id))");
            s.execute("create table announcement_source_events(announcement_id bigint,external_id varchar,source varchar,event_type varchar,details varchar,recorded_at timestamp)");
            int id=1;for(var entry:ContestKoreaRemovalRegistry.REMOVED.entrySet()){
                try(var insert=c.prepareStatement("insert into announcements values(?,'CONTEST_KOREA',?,?,'초등학생',null,'UNCHANGED')")){insert.setInt(1,id);insert.setString(2,entry.getKey());insert.setString(3,entry.getValue());insert.executeUpdate();}
                s.execute("insert into announcement_recommendations values("+id+","+id+")");
                s.execute("insert into announcement_source_events(announcement_id,event_type) values("+id+",'OLD')");id++;
            }
            s.execute("insert into announcements values(50,'CONTEST_KOREA','retained','남길 공고','해당자 ▶','{\"values\":{\"topic\":\"KEEP\",\"target_raw\":\"해당자 ▶\"},\"file\":\"old\"}','UNCHANGED'),(51,'OTHER','202611020027','다른 출처','성인',null,'UNCHANGED')");
        }return url;
    }
    List<Map<String,String>> csv(){return List.of(Map.of("api_id","retained","target_raw","해당자 ▶","target_text","대학생 (만 19세 이상)"),Map.of("api_id","missing","target_text","일반인"));}
    long count(String url,String query)throws Exception{try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getLong(1);}}
    @Test void previewIsReadOnlyAndApplyDeletesExactlyTenWithCorrectionsAndCanRepeat()throws Exception {
        String url=database();
        var preview=ContestKoreaCuration.run(url,"sa","",csv(),"fixture","PREVIEW");
        assertEquals(10,preview.deletionCandidates().size());assertEquals(1,preview.targetCorrectionCandidates());assertEquals(0,preview.correctedTargets());assertEquals(12,count(url,"select count(*) from announcements"));
        var apply=ContestKoreaCuration.run(url,"sa","",csv(),"fixture","APPLY");
        assertEquals(10,apply.deletedAnnouncements());assertEquals(10,apply.deletedRecommendations());assertEquals(10,apply.deletedSourceEvents());assertEquals(0,apply.remainingRemovedAnnouncements());assertEquals(1,apply.correctedTargets());assertEquals(List.of("missing"),apply.unmatchedCsvIds());
        assertEquals(2,count(url,"select count(*) from announcements"));assertEquals(1,count(url,"select count(*) from announcements where source_provider='OTHER'"));
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("select enrollment_requirement,csv_metadata,theme from announcements where announcement_id=50")){
            r.next();assertEquals("대학생 (만 19세 이상)",r.getString(1));assertTrue(r.getString(2).contains("KEEP"));assertTrue(r.getString(2).contains("target_raw"));assertEquals("UNCHANGED",r.getString(3));
        }
        var repeat=ContestKoreaCuration.run(url,"sa","",csv(),"fixture","APPLY");assertEquals(0,repeat.deletedAnnouncements());assertEquals(0,repeat.correctedTargets());assertEquals(1,count(url,"select count(*) from announcement_source_events"));
    }
    @Test void expeditionReferencesBlockAllChangesRatherThanCascadeDeleteUserWork()throws Exception {
        String url=database();try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){s.execute("insert into expeditions values(1,1)");}
        assertFalse(ContestKoreaCuration.run(url,"sa","",csv(),"fixture","PREVIEW").blockingReferences().isEmpty());
        assertThrows(IllegalStateException.class,()->ContestKoreaCuration.run(url,"sa","",csv(),"fixture","APPLY"));
        assertEquals(12,count(url,"select count(*) from announcements"));assertEquals(1,count(url,"select count(*) from announcements where announcement_id=50 and enrollment_requirement='해당자 ▶'"));
    }
    @Test void correctionAuditFailureRollsBackWithoutDeletingRecords()throws Exception {
        String url=database();try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){s.execute("drop table announcement_source_events");}
        assertThrows(SQLException.class,()->ContestKoreaCuration.run(url,"sa","",csv(),"fixture","APPLY"));
        assertEquals(12,count(url,"select count(*) from announcements"));assertEquals(1,count(url,"select count(*) from announcements where announcement_id=50 and enrollment_requirement='해당자 ▶'"));
    }
}
