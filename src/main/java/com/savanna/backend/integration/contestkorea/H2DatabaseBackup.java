package com.savanna.backend.integration.contestkorea;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Offline copy of an existing H2 file. Never connects to or creates the source database. */
public final class H2DatabaseBackup {
    private H2DatabaseBackup() {}

    public static final class BackupException extends IllegalStateException {
        private final String stage;
        private final Path database;
        private final Path backupDirectory;

        public BackupException(String stage, Path database, Path backupDirectory, Throwable cause) {
            super("H2_DATABASE_UNAVAILABLE stage=" + stage, cause);
            this.stage = stage;
            this.database = database;
            this.backupDirectory = backupDirectory;
        }
        public String stage() { return stage; }
        public Path database() { return database; }
        public Path backupDirectory() { return backupDirectory; }
    }

    public static Path backup(String jdbcUrl, Path directory) {
        String prefix = "jdbc:h2:file:";
        if (!jdbcUrl.startsWith(prefix)) return null;
        String configuredPath = jdbcUrl.substring(prefix.length()).split(";", 2)[0];
        if (configuredPath.startsWith("~/")) configuredPath = System.getProperty("user.home") + configuredPath.substring(1);
        Path database = Path.of(configuredPath + ".mv.db").toAbsolutePath().normalize();
        Path backupDirectory = directory.toAbsolutePath().normalize();
        Path destination = null;
        boolean created = false;
        boolean completed = false;
        String stage = "OPEN_SOURCE";
        try {
            // Do not permit a typo/missing source to create a different, empty database on startup.
            if (!Files.readAttributes(database, java.nio.file.attribute.BasicFileAttributes.class).isRegularFile()) {
                throw new IOException("Database source is not a regular file.");
            }
            // WRITE is needed for the exclusive lock; source bytes are only ever read.
            try (var source = FileChannel.open(database, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                stage = "LOCK_SOURCE";
                try (var lock = source.tryLock()) {
                    if (lock == null) throw new IOException("Source database is locked by another process.");
                    stage = "CREATE_BACKUP_DIRECTORY";
                    Files.createDirectories(backupDirectory);
                    stage = "CREATE_BACKUP_FILE";
                    destination = backupDirectory.resolve("savanna-before-contest-import-" + UUID.randomUUID() + ".mv.db");
                    try (var target = FileChannel.open(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        created = true;
                        stage = "COPY_BACKUP";
                        long expected = source.size();
                        long copied = 0;
                        var buffer = ByteBuffer.allocateDirect(64 * 1024);
                        // Windows locks are mandatory: Files.copy would reopen the locked source.
                        // Read using the SAME channel that owns the lock instead.
                        while (source.read(buffer) != -1) {
                            buffer.flip();
                            while (buffer.hasRemaining()) copied += target.write(buffer);
                            buffer.clear();
                        }
                        if (copied != expected) throw new IOException("Backup byte count does not match source.");
                        stage = "FLUSH_BACKUP";
                        target.force(true);
                    }
                    completed = true;
                    stage = "RELEASE_LOCK";
                }
            }
            return destination;
        } catch (IOException | OverlappingFileLockException | SecurityException exception) {
            // Only remove an incomplete backup created by this invocation. Never touch source bytes.
            if (created && !completed) {
                try { Files.deleteIfExists(destination); }
                catch (IOException | SecurityException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            }
            throw new BackupException(stage, database, backupDirectory, exception);
        }
    }
}
