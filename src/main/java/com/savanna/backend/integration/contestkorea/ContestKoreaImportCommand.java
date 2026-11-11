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
            var category = ContestKoreaCategory.valueOf(context.getEnvironment()
                    .getProperty("contest-korea.import.category", "IDEA"));
            List<ContestKoreaItem> items;
            try {
                items = context.getBean(ContestKoreaClient.class).fetch(category);
            } catch (Exception exception) {
                System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));
                // Never print original HTTP exceptions: the request URL carries the API key.
                System.out.println("CONTEST_KOREA_RESULT category=" + category
                        + " created=0 updated=0 skipped=0 failedRequests=1 failedItems=unknown stage=fetch");
                return 1;
            }
            ContestKoreaWriter.ImportResult result;
            try {
                // The transaction commits before this call returns. On failure, all writes roll back.
                result = context.getBean(ContestKoreaWriter.class).save(category, items);
            } catch (Exception exception) {
                System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));
                System.out.println("CONTEST_KOREA_RESULT category=" + category + " fetched=" + items.size()
                        + " created=0 updated=0 skipped=unknown failedItems=" + items.size() + " stage=save rolledBack=true");
                return 1;
            }
            System.out.println("CONTEST_KOREA_RESULT category=" + category + " fetched=" + items.size()
                    + " created=" + result.created() + " updated=" + result.updated()
                    + " skipped=" + result.skippedIds().size() + " failedItems=0 failedRequests=0");
            if (!result.skippedIds().isEmpty()) System.out.println("CONTEST_KOREA_SKIPPED_IDS " + result.skippedIds());
            try {
                verifyList(context, items, Set.copyOf(result.skippedIds()));
            } catch (Exception exception) {
                System.out.println(ContestKoreaFailureDiagnostics.summarize(exception));
                System.out.println("CONTEST_KOREA_API_VERIFY_FAILED: import is committed; inspect list API and verification-user cleanup.");
                return 2;
            }
            return 0;
        }
    }

    private static final class BackupOnlyComplete extends RuntimeException {}

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
                    .filter(a -> a.isVisibility() && !a.getDeadline().isBefore(today))
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
            System.out.println("CONTEST_KOREA_API_VERIFIED method=GET path=/api/v1/announcements status=200 pages="
                    + pages + " expectedVisible=" + expected.size() + " found=" + found.size()
                    + " announcementIds=" + found.stream().sorted().toList());
        } finally {
            users.deleteById(user.getUserId());
            System.out.println("CONTEST_KOREA_VERIFY_USER_REMOVED");
        }
    }
}
