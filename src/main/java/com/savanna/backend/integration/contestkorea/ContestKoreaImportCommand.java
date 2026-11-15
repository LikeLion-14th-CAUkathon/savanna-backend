package com.savanna.backend.integration.contestkorea;

import com.savanna.backend.SavannaBackendApplication;
import com.savanna.backend.domain.User;
import com.savanna.backend.jwt.JwtTokenProvider;
import com.savanna.backend.repository.AnnouncementRepository;
import com.savanna.backend.repository.UserRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Local one-shot entry point. The ordinary application never imports on startup. */
public final class ContestKoreaImportCommand {
    private ContestKoreaImportCommand() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String... args) {
        var application = new SpringApplication(SavannaBackendApplication.class);
        application.addInitializers(context -> {
            var environment = context.getEnvironment();
            String ddl = environment.getProperty("spring.jpa.hibernate.ddl-auto", "update");
            if (ddl.equalsIgnoreCase("create") || ddl.equalsIgnoreCase("create-drop") || ddl.equalsIgnoreCase("drop")) {
                throw new IllegalStateException("Import command does not permit database initialization.");
            }
            var backup = backupH2Database(environment.getProperty("spring.datasource.url", ""),
                    java.nio.file.Path.of(environment.getProperty("contest-korea.import.backup-directory", "tmp/db-backups")));
            if (environment.getProperty("contest-korea.import.backup-only", Boolean.class, false)) {
                if (backup == null) throw new IllegalStateException("Backup-only mode requires an existing file H2 database.");
                verifyBackupConnection(backup, environment);
                throw new BackupOnlyComplete();
            }
            String databaseUrl=environment.getProperty("spring.datasource.url", "");
            if (!databaseUrl.startsWith("jdbc:h2:file:") && !databaseUrl.startsWith("jdbc:h2:mem:")) {
                throw new IllegalStateException("Manual import/migration is restricted to local H2. Do not target EC2 remotely.");
            }
            String audienceMode=environment.getProperty("contest-korea.import.audience-only","");
            if(!audienceMode.isBlank()) {
                try {
                    var result=AnnouncementAudienceMigration.run(databaseUrl,environment.getProperty("spring.datasource.username","sa"),
                            environment.getProperty("spring.datasource.password",""),audienceMode);
                    throw new AudiencePolicyComplete(result);
                }catch(java.sql.SQLException failure){throw new IllegalStateException("Audience policy pass failed.",failure);}
            }
            if (environment.getProperty("contest-korea.import.migrate-fields", Boolean.class, false)) {
                try {
                    String csvPath=environment.getProperty("contest-korea.import.csv", "");
                    var csvRows=csvPath.isBlank()?List.<java.util.Map<String,String>>of():ContestKoreaCsv.read(java.nio.file.Path.of(csvPath));
                    ContestKoreaFieldMigration.apply(environment.getProperty("spring.datasource.url"),
                            environment.getProperty("spring.datasource.username","sa"), environment.getProperty("spring.datasource.password",""),csvRows);
                } catch (java.sql.SQLException | java.io.IOException exception) { throw new IllegalStateException("Explicit field migration failed.",exception); }
            }
        });
        // Fixed local bind and an ephemeral port. Credentials never appear in command arguments.
        var options = new java.util.ArrayList<>(List.of(args));
        options.addAll(List.of("--server.address=127.0.0.1", "--server.port=0",
                "--spring.main.banner-mode=off", "--logging.level.root=OFF", "--spring.sql.init.mode=never",
                "--logging.level.org.springframework.web.client=OFF",
                "--logging.level.org.springframework.http=OFF"));
        ConfigurableApplicationContext context;
        try {
            context = application.run(options.toArray(String[]::new));
        } catch (Exception exception) {
            if(exception instanceof AudiencePolicyComplete complete) {
                var report=JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(complete.result);
                System.out.println("ANNOUNCEMENT_AUDIENCE_RESULT "+report);
                try {
                    var directory=java.nio.file.Path.of("tmp/import-reports");java.nio.file.Files.createDirectories(directory);
                    var path=directory.resolve("announcement-audience-"+UUID.randomUUID()+".json");
                    java.nio.file.Files.writeString(path,report);
                    System.out.println("ANNOUNCEMENT_AUDIENCE_REPORT "+path.toAbsolutePath());
                    return 0;
                }catch(java.io.IOException failure){System.out.println("ANNOUNCEMENT_AUDIENCE_REPORT_FAILED mode="+complete.result.mode()+" changed="+complete.result.changed());return 2;}
            }
            if (exception instanceof BackupOnlyComplete) {
                System.out.println("CONTEST_KOREA_BACKUP_ONLY_COMPLETE importExecuted=false originalDatabaseConnected=false");
                return 0;
            }
            System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));
            System.out.println("CONTEST_KOREA_STARTUP_FAILED: check database lock and application configuration; import not executed.");
            System.out.println("CONTEST_KOREA_RESULT created=0 updated=0 skipped=0 failedItems=0 failedRequests=0 startupFailed=1 importExecuted=false");
            return 1;
        }
        try (context) {
            var selected=ContestKoreaCategory.selected(context.getEnvironment().getProperty("contest-korea.import.category","IDEA"));
            var items=new java.util.ArrayList<ContestKoreaItem>();var skipped=new HashSet<String>();
            var report=new java.util.LinkedHashMap<String,Object>();var categoryReports=new java.util.LinkedHashMap<String,Object>();
            boolean failed=false;
            for(var category:selected){
                List<ContestKoreaItem> fetched;
                try{fetched=context.getBean(ContestKoreaClient.class).fetch(category);}
                catch(Exception exception){failed=true;categoryReports.put(category.name(),java.util.Map.of("failedRequests",1,"failedItems","unknown"));
                    System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));
                    System.out.println("CONTEST_KOREA_RESULT category="+category+" created=0 updated=0 skipped=0 excluded=0 failedRequests=1 failedItems=unknown stage=fetch");continue;}
                try{
                    var result=context.getBean(ContestKoreaWriter.class).save(category,fetched);
                    items.addAll(fetched);skipped.addAll(result.skippedIds());
                    categoryReports.put(category.name(),java.util.Map.of("received",fetched.size(),"created",result.created(),"updated",result.updated(),"excluded",result.excluded(),"failedItems",result.skippedIds().size(),"issues",result.issues(),"skippedIds",result.skippedIds()));
                    System.out.println("CONTEST_KOREA_RESULT category="+category+" fetched="+fetched.size()+" created="+result.created()+" updated="+result.updated()+" skipped="+result.skippedIds().size()+" failedItems="+result.skippedIds().size()+" failedRequests=0 excluded="+result.excluded());
                    if(!result.issues().isEmpty())System.out.println("CONTEST_KOREA_ISSUES "+result.issues());
                }catch(Exception exception){failed=true;categoryReports.put(category.name(),java.util.Map.of("received",fetched.size(),"failedItems",fetched.size(),"rolledBack",true));System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));}
            }
            report.put("categories",categoryReports);report.put("datasetComplete",false);report.put("coverage","LATEST_BATCH_ONLY; requestedLimit=100; pagination and total count are not documented");
            System.out.println("CONTEST_KOREA_COVERAGE datasetComplete=false mode=LATEST_BATCH_ONLY requestedLimit=100 pagination=UNDOCUMENTED totalAvailable=UNKNOWN");
            String csvPath=context.getEnvironment().getProperty("contest-korea.import.csv","");
            if(!csvPath.isBlank())try{
                var rows=ContestKoreaCsv.read(java.nio.file.Path.of(csvPath));
                String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(csvPath))));
                var csvResult=context.getBean(ContestKoreaCsvWriter.class).apply(rows,java.nio.file.Path.of(csvPath).getFileName()+":SHA256="+hash);
                report.put("csv",csvResult);System.out.println("CONTEST_KOREA_CSV_RESULT "+JsonMapper.builder().build().writeValueAsString(csvResult));
                if(csvResult.failed()>0)failed=true;
                for(var row:rows)items.add(new ContestKoreaItem(row.get("api_id"),null,null,null,null,null,null,null,null));
            }catch(Exception exception){failed=true;report.put("csvFailure",true);System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));}
            var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            var duplicates=jdbc.queryForList("select source_provider,external_id,count(*) as copies from announcements where source_provider='CONTEST_KOREA' group by source_provider,external_id having count(*)>1");
            report.put("duplicateExternalIds",duplicates);
            var census=jdbc.queryForList("select field,count(*) as stored,sum(case when visibility=true and exclude_reason is null and classification_issue is null then 1 else 0 end) as public_count,sum(case when exclude_reason is not null then 1 else 0 end) as excluded,sum(case when classification_issue is not null then 1 else 0 end) as classification_held from announcements where source_provider='CONTEST_KOREA' group by field order by field");
            report.put("finalDatabaseCounts",census);
            report.put("multipleCategoryMembership",jdbc.queryForList("select external_id,external_category_codes,primary_category_codes,field,classification_issue from announcements where source_provider='CONTEST_KOREA' and external_category_codes like '%|%' order by external_id"));
            report.put("unresolvedClassification",jdbc.queryForList("select external_id,external_category,classification_issue from announcements where source_provider='CONTEST_KOREA' and classification_issue is not null order by external_id"));
            report.put("dateDiscrepancies",jdbc.queryForList("select external_id,cast(api_start_date as varchar) as api_start_date,cast(api_deadline as varchar) as api_deadline,cast(csv_start_date as varchar) as csv_start_date,cast(csv_deadline as varchar) as csv_deadline,date_source from announcements where source_provider='CONTEST_KOREA' and ((csv_start_date is not null and api_start_date<>csv_start_date) or (csv_deadline is not null and api_deadline<>csv_deadline)) order by external_id"));
            System.out.println("CONTEST_KOREA_FINAL_DATABASE "+JsonMapper.builder().build().writeValueAsString(census));
            System.out.println("CONTEST_KOREA_DUPLICATE_EXTERNAL_IDS count="+duplicates.size());
            if(!duplicates.isEmpty())failed=true;
            try {
                verifyList(context, items, skipped);
            } catch (Exception exception) {
                System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));
                System.out.println("CONTEST_KOREA_API_VERIFY_FAILED: import is committed; inspect list API and verification-user cleanup.");
                return 2;
            }
            try{
                var directory=java.nio.file.Path.of("tmp/import-reports");java.nio.file.Files.createDirectories(directory);
                var path=directory.resolve("contest-korea-"+UUID.randomUUID()+".json");
                java.nio.file.Files.writeString(path,JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
                System.out.println("CONTEST_KOREA_REPORT "+path.toAbsolutePath());
            }catch(java.io.IOException exception){throw new IllegalStateException("Cannot write import report",exception);}
            return failed?1:0;
        }
    }

    private static final class BackupOnlyComplete extends RuntimeException {}
    private static final class AudiencePolicyComplete extends RuntimeException {
        private final AnnouncementAudienceMigration.Result result;
        private AudiencePolicyComplete(AnnouncementAudienceMigration.Result result){this.result=result;}
    }

    private static java.nio.file.Path backupH2Database(String jdbcUrl, java.nio.file.Path backupDirectory) {
        var destination = H2DatabaseBackup.backup(jdbcUrl, backupDirectory);
        if (destination != null) System.out.println("CONTEST_KOREA_DB_BACKUP " + destination);
        return destination;
    }

    private static void verifyBackupConnection(java.nio.file.Path backup, org.springframework.core.env.Environment environment) {
        // Verify configured credentials against the snapshot, not the original DB. No JPA/schema initialization.
        String file = backup.toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + file.substring(0, file.length() - ".mv.db".length())
                + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
        try (var connection = java.sql.DriverManager.getConnection(url,
                environment.getProperty("spring.datasource.username", "sa"),
                environment.getProperty("spring.datasource.password", ""))) {
            System.out.println("CONTEST_KOREA_BACKUP_CONNECTION_VERIFIED product=" + connection.getMetaData().getDatabaseProductName()
                    + " readOnly=" + connection.isReadOnly() + " ddlExecuted=false originalDatabaseConnected=false");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Backup JDBC connection check failed.", exception);
        }
    }

    private static void verifyList(ConfigurableApplicationContext context, List<ContestKoreaItem> items,
                                   Set<String> skippedIds) throws Exception {
        var announcements = context.getBean(AnnouncementRepository.class);
        var expected = new HashSet<Long>();
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (var item : items) {
            if (item == null || item.id() == null || skippedIds.contains(item.id().trim())) continue;
            announcements.findBySourceProviderAndExternalId("CONTEST_KOREA", item.id().trim())
                    .filter(a -> a.isAvailable() && !a.getDeadline().isBefore(today))
                    .ifPresent(a -> expected.add(a.getAnnouncementId()));
        }
        var users = context.getBean(UserRepository.class);
        String suffix = UUID.randomUUID().toString();
        var user = users.saveAndFlush(new User("contest-import-" + suffix + "@example.invalid", "contest-import-" + suffix));
        try {
            String token = context.getBean(JwtTokenProvider.class).createAccessToken(user.getUserId());
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var mapper = JsonMapper.builder().build();
            var found = new HashSet<Long>();
            int pages = 0;
            while (true) {
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                                + "/api/v1/announcements?page=" + pages + "&size=100"))
                        .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("List API did not return 200");
                var page = mapper.readTree(response.body());
                for (var announcement : page.get("content")) {
                    long id = announcement.get("announcementId").asLong();
                    if (expected.contains(id)) found.add(id);
                }
                pages++;
                if (page.get("last").asBoolean()) break;
            }
            if (!found.containsAll(expected)) throw new IllegalStateException("Imported announcements missing from list");
            for(var field:com.savanna.backend.enums.AnnouncementField.values()){
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/announcements?field="+field.name()+"&size=100"))
                        .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).GET().build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Field API not 200");
                var page=mapper.readTree(response.body());for(var row:page.get("content"))if(!field.name().equals(row.get("field").asText()))throw new IllegalStateException("Field API mismatch");
                System.out.println("CONTEST_KOREA_FIELD_VERIFIED field="+field+" status=200 totalElements="+page.get("totalElements").asLong());
            }
            System.out.println("CONTEST_KOREA_API_VERIFIED method=GET path=/api/v1/announcements status=200 pages="
                    + pages + " expectedVisible=" + expected.size() + " found=" + found.size()
                    + " announcementIds=" + found.stream().sorted().toList());
        } finally {
            users.deleteById(user.getUserId());
            System.out.println("CONTEST_KOREA_VERIFY_USER_REMOVED");
        }
    }
}
