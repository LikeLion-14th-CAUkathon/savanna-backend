package com.savanna.backend.announcement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class TeamPhotoMigrationTest {
    @Test void existingTeamRemainsWithNoInventedPhotoAndScriptCanRepeat() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:photo-"+UUID.randomUUID(),"sa","");var s=connection.createStatement()){
            s.execute("create table team(team_id bigint primary key,name varchar(100))");s.execute("insert into team values(1,'기존 팀')");
            var script=new FileSystemResource("docs/sql/team-photo-url-h2.sql");ScriptUtils.executeSqlScript(connection,script);ScriptUtils.executeSqlScript(connection,script);
            var keyScript=new FileSystemResource("docs/sql/team-photo-object-key-h2.sql");
            ScriptUtils.executeSqlScript(connection,keyScript);ScriptUtils.executeSqlScript(connection,keyScript);
            try(var r=s.executeQuery("select team_id,name,photo_url,photo_object_key from team")){assertTrue(r.next());assertEquals(1,r.getLong(1));assertEquals("기존 팀",r.getString(2));assertNull(r.getString(3));assertNull(r.getString(4));assertFalse(r.next());}
        }
    }
}
