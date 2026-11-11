package com.savanna.backend.announcement;

import com.savanna.backend.integration.contestkorea.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.channels.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class H2DatabaseBackupTest {
    @TempDir Path directory;

    String jdbc(Path base) { return "jdbc:h2:file:" + base.toString().replace('\\', '/'); }

    @Test void copiesThroughLockOwnerChannelAndPreservesAllSourceBytes() throws Exception {
        var base = directory.resolve("source");
        var source = Path.of(base + ".mv.db");
        byte[] original = new byte[160_001];
        new java.util.Random(42).nextBytes(original);
        Files.write(source, original);
        var backup = H2DatabaseBackup.backup(jdbc(base), directory.resolve("backups"));
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(original, Files.readAllBytes(backup));
        assertNotEquals(source, backup);
    }

    @Test void reportsLockStageAndKeepsOriginalCauseWithoutWritingBackup() throws Exception {
        var base = directory.resolve("source");
        var source = Path.of(base + ".mv.db");
        Files.writeString(source, "original");
        try (var channel = FileChannel.open(source, StandardOpenOption.READ, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            var failure = assertThrows(H2DatabaseBackup.BackupException.class,
                    () -> H2DatabaseBackup.backup(jdbc(base), directory.resolve("backups")));
            assertEquals("LOCK_SOURCE", failure.stage());
            assertInstanceOf(OverlappingFileLockException.class, failure.getCause());
            assertFalse(Files.exists(directory.resolve("backups")));
        }
        assertEquals("original", Files.readString(source));
    }

    @Test void reportsBackupPathFailureAndDoesNotMisclassifyAsSourceLock() throws Exception {
        var base = directory.resolve("source");
        var source = Path.of(base + ".mv.db");
        Files.writeString(source, "original");
        var notDirectory = Files.writeString(directory.resolve("backups"), "existing file");
        var failure = assertThrows(H2DatabaseBackup.BackupException.class,
                () -> H2DatabaseBackup.backup(jdbc(base), notDirectory));
        assertEquals("CREATE_BACKUP_DIRECTORY", failure.stage());
        assertInstanceOf(FileAlreadyExistsException.class, failure.getCause());
        assertEquals(source.toAbsolutePath(), failure.database());
        assertEquals("original", Files.readString(source));
        assertEquals("existing file", Files.readString(notDirectory));
        assertTrue(ContestKoreaFailureDiagnostics.summarize(failure).contains("SQLState=N/A"));
    }

    @Test void missingDatabaseIsAnErrorAndNeverCreatesAnEmptySource() {
        var base = directory.resolve("missing");
        var failure = assertThrows(H2DatabaseBackup.BackupException.class,
                () -> H2DatabaseBackup.backup(jdbc(base), directory.resolve("backups")));
        assertEquals("OPEN_SOURCE", failure.stage());
        assertInstanceOf(NoSuchFileException.class, failure.getCause());
        assertFalse(Files.exists(Path.of(base + ".mv.db")));
    }

    @Test void diagnosticsKeepSqlStatesAndCodesButNeverPrintMessagesOrCredentials() {
        var sql = new SQLException("password=private-password&apikey=private-key", "28000", 28000);
        sql.setNextException(new SQLException("more-private-information", "HY000", 90020));
        var wrapped = new H2DatabaseBackup.BackupException("CHECK_BACKUP", directory.resolve("source"), directory, sql);
        assertSame(sql, wrapped.getCause());
        var diagnostic = ContestKoreaFailureDiagnostics.summarize(wrapped);
        assertTrue(diagnostic.contains("SQLState=28000 errorCode=28000"));
        assertTrue(diagnostic.contains("SQLState=HY000 errorCode=90020"));
        assertFalse(diagnostic.contains("private"));
        assertSame(sql.getNextException(), ((SQLException) wrapped.getCause()).getNextException());
    }

    @Test void backupOnlyChecksCredentialsOnReadOnlySnapshotWithoutChangingOriginalDatabase() throws Exception {
        var base = directory.resolve("existing");
        String url = jdbc(base);
        try (var connection = DriverManager.getConnection(url, "sa", "backup-private-password");
             var statement = connection.createStatement()) {
            statement.execute("create table marker (id int primary key)");
            statement.execute("insert into marker values (42)");
        }
        var source = Path.of(base + ".mv.db");
        byte[] original = Files.readAllBytes(source);
        var captured = new java.io.ByteArrayOutputStream();
        var originalOut = System.out;
        try (var output = new java.io.PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            assertEquals(0, ContestKoreaImportCommand.run("--contest-korea.import.backup-only=true",
                    "--contest-korea.import.backup-directory=" + directory.resolve("backups"),
                    "--spring.datasource.url=" + url, "--spring.datasource.username=sa",
                    "--spring.datasource.password=backup-private-password", "--spring.jpa.hibernate.ddl-auto=update"));
        } finally { System.setOut(originalOut); }
        assertArrayEquals(original, Files.readAllBytes(source));
        String result = captured.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("CONTEST_KOREA_BACKUP_ONLY_COMPLETE"));
        assertTrue(result.contains("readOnly=true"));
        assertFalse(result.contains("backup-private-password"));
        assertFalse(result.contains("CONTEST_KOREA_RESULT category="));
        try (var files = Files.list(directory.resolve("backups"))) {
            assertEquals(1, files.count());
        }
    }

    @Test void snapshotConnectionFailureReportsActualSqlStateAndCodeWithoutPrintingPassword() throws Exception {
        var base = directory.resolve("protected");
        String url = jdbc(base);
        try (var connection = DriverManager.getConnection(url, "sa", "correct-private-password")) {
            connection.createStatement().close();
        }
        var source = Path.of(base + ".mv.db");
        byte[] original = Files.readAllBytes(source);
        var captured = new java.io.ByteArrayOutputStream();
        var originalOut = System.out;
        try (var output = new java.io.PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            assertEquals(1, ContestKoreaImportCommand.run("--contest-korea.import.backup-only=true",
                    "--contest-korea.import.backup-directory=" + directory.resolve("backups"),
                    "--spring.datasource.url=" + url, "--spring.datasource.username=sa",
                    "--spring.datasource.password=wrong-private-password", "--spring.jpa.hibernate.ddl-auto=update"));
        } finally { System.setOut(originalOut); }
        assertArrayEquals(original, Files.readAllBytes(source));
        String result = captured.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("SQLState=28000 errorCode=28000"));
        assertFalse(result.contains("wrong-private-password"));
        assertFalse(result.contains("correct-private-password"));
    }
}
