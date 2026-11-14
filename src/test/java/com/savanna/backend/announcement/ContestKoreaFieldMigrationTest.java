package com.savanna.backend.announcement;
import com.savanna.backend.integration.contestkorea.ContestKoreaFieldMigration;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ContestKoreaFieldMigrationTest {
    @Test void migratesLegacyConstraintAndHoldsUnknownSocialWithoutGuessingAndCanRepeat() throws Exception {
        String url="jdbc:h2:mem:field-migration-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
            s.execute("create table announcements(announcement_id bigint primary key,external_id varchar,field varchar check(field in ('SOCIAL_ESG','IDEA_PLANNING')),external_category varchar,source_url varchar,source_provider varchar,theme varchar,evaluation_criteria varchar,submission_requirements varchar,enrollment_requirement varchar)");
            s.execute("insert into announcements(announcement_id,external_id,field,external_category,source_provider,theme) values(1,'known','SOCIAL_ESG','학문•과학•IT','CONTEST_KOREA','원문 참고'),(2,'unknown','SOCIAL_ESG',null,null,null),(3,'idea','IDEA_PLANNING',null,null,null)");
        }
        ContestKoreaFieldMigration.apply(url,"sa","",List.of());ContestKoreaFieldMigration.apply(url,"sa","",List.of());
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("select field,classification_issue,theme from announcements order by announcement_id")){
            assertTrue(r.next());assertEquals("ACADEMIC_SCIENCE_IT",r.getString(1));assertNull(r.getString(3));
            assertTrue(r.next());assertNull(r.getString(1));assertNotNull(r.getString(2));
            assertTrue(r.next());assertEquals("IDEA_ARCHITECTURE_STARTUP",r.getString(1));
        }
    }
}
