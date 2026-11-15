package com.savanna.backend.announcement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class RequestedContestExclusionSqlTest {
    @Test void hidesOnlyRequestedSourceIdsPreservesReasonsAndDoesNotDuplicateAudit() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID(),"sa","");var s=c.createStatement()){
            s.execute("create table announcements(announcement_id bigint primary key,external_id varchar,source_provider varchar,title varchar,visibility boolean,exclude_reason varchar,classification_issue varchar)");
            s.execute("create table announcement_source_events(announcement_id bigint,external_id varchar,source varchar,event_type varchar,details varchar,recorded_at timestamp)");
            s.execute("insert into announcements values(1,'202611040033','CONTEST_KOREA','어린이 미래 상상 그림 공모전',true,null,null),(2,'202610170003','CONTEST_KOREA','2026 ACEL 전국 청소년 미술 공모전',true,'기존 사유',null),(3,'202611040033','OTHER','타 출처',true,null,null),(4,'other-id','CONTEST_KOREA','다른 공고',true,null,null)");
            var script=new FileSystemResource("docs/sql/exclude-two-youth-contests-h2.sql");ScriptUtils.executeSqlScript(c,script);ScriptUtils.executeSqlScript(c,script);
            try(var r=s.executeQuery("select count(*) from announcements where announcement_id in(1,2) and visibility=false and exclude_reason like '%[MANUAL_REQUEST]%'")){r.next();assertEquals(2,r.getInt(1));}
            try(var r=s.executeQuery("select exclude_reason from announcements where announcement_id=2")){r.next();assertTrue(r.getString(1).startsWith("기존 사유\n"));}
            try(var r=s.executeQuery("select count(*) from announcements where announcement_id in(3,4) and visibility=true and exclude_reason is null")){r.next();assertEquals(2,r.getInt(1));}
            try(var r=s.executeQuery("select count(*) from announcement_source_events")){r.next();assertEquals(2,r.getInt(1));}
            try(var r=s.executeQuery("select count(*) from announcements")){r.next();assertEquals(4,r.getInt(1));}
        }
    }
}
