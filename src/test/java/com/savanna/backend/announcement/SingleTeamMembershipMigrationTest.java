package com.savanna.backend.announcement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class SingleTeamMembershipMigrationTest {
    @Test void uniqueIndexPreservesRowsRejectsDuplicatesAndCanRepeat() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID(),"sa","");var s=c.createStatement()){
            s.execute("create table team_member(user_id bigint,team_id bigint)");s.execute("insert into team_member values(1,10)");
            var script=new FileSystemResource("docs/sql/single-team-membership-h2.sql");ScriptUtils.executeSqlScript(c,script);ScriptUtils.executeSqlScript(c,script);
            assertThrows(java.sql.SQLException.class,()->s.execute("insert into team_member values(1,20)"));
            try(var r=s.executeQuery("select count(*) from team_member")){r.next();assertEquals(1,r.getInt(1));}
        }
    }
    @Test void existingConflictsStopMigrationWithoutDeletingOrChoosingTeam() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID(),"sa","");var s=c.createStatement()){
            s.execute("create table team_member(user_id bigint,team_id bigint)");s.execute("insert into team_member values(1,10),(1,20)");
            assertThrows(org.springframework.jdbc.datasource.init.ScriptException.class,()->ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/sql/single-team-membership-h2.sql")));
            try(var r=s.executeQuery("select count(*) from team_member")){r.next();assertEquals(2,r.getInt(1));}
        }
    }
}
