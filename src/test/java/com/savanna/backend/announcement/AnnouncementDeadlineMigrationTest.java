package com.savanna.backend.announcement;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Types;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementDeadlineMigrationTest {
    @Test void migrationKeepsExistingDateAndChangesColumnToDate() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:deadline-migration");
             var statement = connection.createStatement()) {
            statement.execute("create table announcements (announcement_id bigint primary key, deadline timestamp not null)");
            statement.execute("insert into announcements values (1, TIMESTAMP '2026-12-20 23:59:59'), (2, TIMESTAMP '2027-01-01 00:00:00')");
            statement.execute(Files.readString(Path.of("docs/sql/announcement-deadline-date-h2.sql")));
            try (var rows = statement.executeQuery("select deadline from announcements order by announcement_id")) {
                assertEquals(Types.DATE, rows.getMetaData().getColumnType(1));
                assertTrue(rows.next());
                assertEquals(LocalDate.of(2026, 12, 20), rows.getObject(1, LocalDate.class));
                assertTrue(rows.next());
                assertEquals(LocalDate.of(2027, 1, 1), rows.getObject(1, LocalDate.class));
            }
        }
    }
}
