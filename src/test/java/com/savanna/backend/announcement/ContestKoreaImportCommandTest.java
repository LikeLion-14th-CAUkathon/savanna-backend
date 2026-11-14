package com.savanna.backend.announcement;

import com.savanna.backend.integration.contestkorea.ContestKoreaImportCommand;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class ContestKoreaImportCommandTest {
    @Test void manualCommandCommitsVerifiesHttpAndCleansUpTemporaryUserWithoutPrintingKey() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/meeting_info_api.php", exchange -> {
            var body = """
                    [{"resultcode":"1","resultmsg":"성공","Data":[{
                    "STR_MEETCODE":"command-test", "STR_MEETING":"수집 실행 검증", "STR_HOST":"주최",
                    "STR_MENU":"아이디어•건축•창업", "STR_TARGET":"해당자 ▶", "STR_AREA":"온라인",
                    "STR_DATE1":"11.01~12.31", "STR_URL":"https://www.contestkorea.com/test"}]}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String db = "jdbc:h2:mem:contest-command-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        var captured = new java.io.ByteArrayOutputStream();
        var original = System.out;
        try (var output = new java.io.PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            String[] args = {"--contest-korea.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                    "--contest-korea.api-key=command-private-key", "--contest-korea.import.category=IDEA",
                    "--spring.datasource.url=" + db, "--spring.jpa.hibernate.ddl-auto=update"};
            assertEquals(0, ContestKoreaImportCommand.run(args));
            assertEquals(0, ContestKoreaImportCommand.run(args));
        } finally {
            System.setOut(original);
            server.stop(0);
        }
        String result = captured.toString(StandardCharsets.UTF_8);
        assertFalse(result.contains("command-private-key"));
        assertTrue(result.contains("created=1 updated=0 skipped=0 failedItems=0"));
        assertTrue(result.contains("created=0 updated=1 skipped=0 failedItems=0"));
        assertTrue(result.contains("CONTEST_KOREA_API_VERIFIED"));
        assertTrue(result.contains("CONTEST_KOREA_VERIFY_USER_REMOVED"));
        try (var connection = DriverManager.getConnection(db, "sa", ""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select count(*) from announcements where external_id='command-test'")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("select count(*) from users where google_sub like 'contest-import-%'")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
        }
    }
}
