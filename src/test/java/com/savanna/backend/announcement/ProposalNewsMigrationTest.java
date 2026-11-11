package com.savanna.backend.announcement;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ProposalNewsMigrationTest {
    @Test void additiveMigrationPreservesExistingReadAndResponseDataAndIsRepeatable() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:news-migration");
             var statement = connection.createStatement()) {
            statement.execute("create table proposal_recipients (proposal_recipient_id bigint primary key, status varchar(20), read_at timestamp, responded_at timestamp)");
            statement.execute("insert into proposal_recipients values (1,'JOINED',TIMESTAMP '2026-11-10 10:00:00',TIMESTAMP '2026-11-10 11:00:00'), (2,'PENDING',null,null)");
            String sql = Files.readString(Path.of("docs/sql/proposal-response-news-h2.sql"));
            for (int pass = 0; pass < 2; pass++) for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
            try (var rows = statement.executeQuery("select * from proposal_recipients order by proposal_recipient_id")) {
                assertTrue(rows.next());
                assertEquals("JOINED", rows.getString("status"));
                assertEquals(LocalDateTime.of(2026, 11, 10, 10, 0), rows.getObject("read_at", LocalDateTime.class));
                assertEquals(LocalDateTime.of(2026, 11, 10, 11, 0), rows.getObject("responded_at", LocalDateTime.class));
                assertEquals(0, rows.getLong("response_version"));
                assertEquals(0, rows.getLong("response_read_version"));
                assertNull(rows.getObject("response_read_at"));
                assertTrue(rows.next());
                assertEquals("PENDING", rows.getString("status"));
                assertNull(rows.getObject("read_at"));
                assertNull(rows.getObject("responded_at"));
                assertFalse(rows.next());
            }
        }
    }
}
