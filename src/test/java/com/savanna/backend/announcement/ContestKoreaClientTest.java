package com.savanna.backend.announcement;

import com.savanna.backend.integration.contestkorea.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ContestKoreaClientTest {
    @Test void parsesActualEnvelopeAndUsesConfiguredCategoryAndKey() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var query = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/api/meeting_info_api.php", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = """
                    [{"resultcode":"1","resultmsg":"성공","Data":[{
                    "STR_MEETCODE":"202611030022","STR_MENU":"아이디어•건축•창업",
                    "STR_MEETING":"2026 PAC 공모전","STR_HOST":"PAC 운영위원회",
                    "STR_TARGET":"대학생","STR_DATE1":"10.30~11.18","STR_AREA":"온라인",
                    "STR_URL":"https://www.contestkorea.com/sub/view.php?str_no=202611030022",
                    "STR_IMG":"https://www.contestkorea.com/poster.jpg","INT_FREEYN":"0","STR_SERVICE":"Y"}]}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new ContestKoreaClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            var items = client.fetch(ContestKoreaCategory.IDEA);
            assertEquals(1, items.size());
            assertEquals("2026 PAC 공모전", items.get(0).title());
            assertEquals("온라인", items.get(0).participationRegion());
            assertTrue(query.get().contains("apikey=test-key"));
            assertTrue(query.get().contains("Txt_bcode=031410001"));
            assertTrue(query.get().contains("limitsu=100"));
        } finally { server.stop(0); }
    }

    @Test void httpFailureDoesNotExposeKeyAndMissingKeyFailsBeforeRequest() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/meeting_info_api.php", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var failure = assertThrows(IllegalStateException.class,
                    () -> new ContestKoreaClient(url, "private-key").fetch(ContestKoreaCategory.IDEA));
            assertFalse(failure.toString().contains("private-key"));
            assertNull(failure.getCause());
            assertThrows(IllegalStateException.class, () -> new ContestKoreaClient(url, "").fetch(ContestKoreaCategory.IDEA));
        } finally { server.stop(0); }
    }

    @Test void parsesSeparatorsSameDayAndCalendarValidation() {
        assertEquals(LocalDate.of(2026, 11, 18), ContestKoreaPeriod.parse(" 11.18 ~ 11.18 ").deadline());
        assertEquals(LocalDate.of(2027, 1, 23), ContestKoreaPeriod.parse("11.18-01.23").deadline());
        for (String invalid : new String[]{"02.29~03.01", "11.31~12.01", "상시 접수", "", "13.01~01.02"}) {
            assertThrows(IllegalArgumentException.class, () -> ContestKoreaPeriod.parse(invalid));
        }
    }
}
