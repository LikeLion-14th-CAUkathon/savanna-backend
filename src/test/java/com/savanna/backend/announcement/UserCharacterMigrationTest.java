package com.savanna.backend.announcement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UserCharacterMigrationTest {
    @Test void keepsExistingUserUnassignedAndCanRepeatAndRejectsUnknownIds() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:character-"+UUID.randomUUID(),"sa","");var statement=connection.createStatement()) {
            statement.execute("create table users(user_id bigint primary key,email varchar(255))");
            statement.execute("insert into users values(1,'existing@example.invalid')");
            var script=new FileSystemResource("docs/sql/user-character-id-h2.sql");
            ScriptUtils.executeSqlScript(connection,script);ScriptUtils.executeSqlScript(connection,script);
            try(var row=statement.executeQuery("select user_id,email,character_id from users")){assertTrue(row.next());assertEquals(1,row.getLong(1));assertEquals("existing@example.invalid",row.getString(2));assertNull(row.getObject(3));assertFalse(row.next());}
            statement.execute("update users set character_id=4 where user_id=1");
            assertThrows(SQLException.class,()->statement.execute("update users set character_id=10 where user_id=1"));
        }
    }
}
