package com.savanna.backend.announcement;

import com.savanna.backend.integration.contestkorea.AnnouncementAudienceMigration;
import com.savanna.backend.service.AnnouncementAudiencePolicy;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementAudienceMigrationTest {
    private String database(boolean auditTable) throws Exception {
        String url="jdbc:h2:mem:audience-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.execute("create table announcements(announcement_id bigint primary key,external_id varchar,title varchar,enrollment_requirement varchar,exclude_reason varchar,visibility boolean)");
            s.execute("insert into announcements values(1,'youth','공모전','초등학생, 중학생, 고등학생',null,true),(2,'mixed','공모전','고등학생, 대학생',null,true),(3,'adult','공모전','일반인','수동 사유',false),(4,'review','청소년 공모전',null,null,true),(5,'already-hidden','공모전','청소년','기존 제외 사유',false)");
            if(auditTable)s.execute("create table announcement_source_events(announcement_id bigint,external_id varchar,source varchar,event_type varchar,details varchar,recorded_at timestamp)");
        }
        return url;
    }
    @Test void previewDoesNotChangeRowsAndApplyPreservesReasonsAndIsIdempotent() throws Exception {
        String url=database(true);
        var preview=AnnouncementAudienceMigration.run(url,"sa","","PREVIEW");
        assertEquals(5,preview.scanned());assertEquals(2,preview.exclusionCandidates());assertEquals(0,preview.changed());assertEquals(1,preview.review().size());
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("select visibility,exclude_reason from announcements where announcement_id=1")){assertTrue(r.next());assertTrue(r.getBoolean(1));assertNull(r.getString(2));}
        var apply=AnnouncementAudienceMigration.run(url,"sa","","APPLY");assertEquals(2,apply.changed());
        assertEquals(0,AnnouncementAudienceMigration.run(url,"sa","","APPLY").changed());
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("select exclude_reason from announcements where announcement_id=5")){assertTrue(r.next());assertEquals("기존 제외 사유\n"+AnnouncementAudiencePolicy.EXCLUDE_REASON,r.getString(1));}
    }
    @Test void auditFailureRollsBackExclusionUpdate() throws Exception {
        String url=database(false);
        assertThrows(SQLException.class,()->AnnouncementAudienceMigration.run(url,"sa","","APPLY"));
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("select visibility,exclude_reason from announcements where announcement_id=1")){assertTrue(r.next());assertTrue(r.getBoolean(1));assertNull(r.getString(2));}
    }
}
