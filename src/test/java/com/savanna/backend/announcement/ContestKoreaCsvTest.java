package com.savanna.backend.announcement;
import com.savanna.backend.integration.contestkorea.ContestKoreaCsv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ContestKoreaCsvTest {
    @TempDir Path directory;
    @Test void readsBomQuotedCommasQuotesAndMultilineWithoutChangingSource() throws Exception {
        var path=directory.resolve("source.csv");var text="\uFEFFapi_id,category_source,topic\r\n1,아이디어•건축•창업,\"쉼표,와 \"\"인용\"\"\n다음 줄\"\r\n";
        Files.writeString(path,text);var rows=ContestKoreaCsv.read(path);
        assertEquals(1,rows.size());assertEquals("쉼표,와 \"인용\"\n다음 줄",rows.get(0).get("topic"));assertEquals(text,Files.readString(path));
    }
}
