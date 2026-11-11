package com.savanna.backend.integration.contestkorea;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Whitelisted diagnostic fields; never logs exception messages, SQL text, URLs or credentials. */
public final class ContestKoreaFailureDiagnostics {
    private ContestKoreaFailureDiagnostics() {}

    public static String summarize(Throwable failure) {
        var output = new StringBuilder("CONTEST_KOREA_FAILURE");
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        pending.add(failure);
        boolean hasSql = false;
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (!visited.add(current)) continue;
            output.append(" cause=").append(current.getClass().getName());
            if (current instanceof H2DatabaseBackup.BackupException backup) {
                output.append(" stage=").append(backup.stage()).append(" database=").append(backup.database())
                        .append(" backupDirectory=").append(backup.backupDirectory());
            }
            if (current instanceof SQLException sql) {
                hasSql = true;
                output.append(" SQLState=").append(sql.getSQLState()).append(" errorCode=").append(sql.getErrorCode());
                if (sql.getNextException() != null) pending.add(sql.getNextException());
            }
            if (current.getCause() != null) pending.add(current.getCause());
            for (var suppressed : current.getSuppressed()) pending.add(suppressed);
        }
        if (!hasSql) output.append(" SQLState=N/A errorCode=N/A (no SQLException)");
        return output.toString();
    }
}
