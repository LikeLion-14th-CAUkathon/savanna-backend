package com.savanna.backend.announcement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementConsentMigrationTest {
    @Test void addsNullableConsentWithoutChangingExistingRowAndCanRepeat() throws Exception {
        String url="jdbc:h2:mem:consent-migration-"+UUID.randomUUID();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()){
            statement.execute("create table announcements(announcement_id bigint primary key,title varchar(300))");
            statement.execute("insert into announcements values(1,'기존 공고')");
            var script=new FileSystemResource("docs/sql/announcement-consent-requirements-h2.sql");
            ScriptUtils.executeSqlScript(connection,script);ScriptUtils.executeSqlScript(connection,script);
            try(var result=statement.executeQuery("select announcement_id,title,consent_requirements from announcements")){
                assertTrue(result.next());assertEquals(1,result.getLong(1));assertEquals("기존 공고",result.getString(2));assertNull(result.getString(3));assertFalse(result.next());
            }
        }
    }
}
