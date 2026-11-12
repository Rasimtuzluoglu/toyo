package tr.com.servisdefteri.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.settings.BusinessSettings;
import tr.com.servisdefteri.settings.BusinessSettingsService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    @TempDir
    Path tempDir;

    private BackupProperties properties;
    private BackupService service;
    private Path backupDirectory;

    @BeforeEach
    void setUp() {
        properties = new BackupProperties();
        backupDirectory = tempDir.resolve("backups");
        properties.setDirectory(backupDirectory.toString());
        properties.setRetentionDays(14);
        properties.setMinCopies(2);
        properties.setVerify(false);
        properties.setTimeoutSeconds(60);

        BackupRunRepository repository = mock(BackupRunRepository.class);
        when(repository.save(any(BackupRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BusinessSettingsService settings = mock(BusinessSettingsService.class);
        when(settings.get()).thenReturn(new BusinessSettings());
        AuditService auditService = mock(AuditService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new BackupService(repository, properties, settings, auditService, clock);
    }

    @Test
    void failedDumpRemovesPartialFile() throws Exception {
        properties.setPgDumpPath(fakeDumpScript(false).toString());

        BackupRun run = service.runBackup("TEST");

        assertThat(run.getStatus()).isEqualTo(BackupStatus.BASARISIZ);
        assertThat(dumpFiles()).isEmpty();
    }

    @Test
    void hungDumpTimesOutAndRemovesPartialFile() throws Exception {
        properties.setTimeoutSeconds(1);
        properties.setPgDumpPath(fakeDumpScript(true).toString());

        BackupRun run = service.runBackup("TEST");

        assertThat(run.getStatus()).isEqualTo(BackupStatus.BASARISIZ);
        assertThat(run.getMessage()).contains("zaman aşımı");
        assertThat(dumpFiles()).isEmpty();
    }

    @Test
    void successfulBackupCanWriteSecondCopy() throws Exception {
        Path secondary = tempDir.resolve("secondary");
        properties.setPgDumpPath(successfulDumpScript().toString());
        properties.setCopyDirectory(secondary.toString());

        BackupRun run = service.runBackup("TEST");

        assertThat(run.getStatus()).isEqualTo(BackupStatus.BASARILI);
        try (Stream<Path> files = Files.list(secondary)) {
            assertThat(files.filter(path -> path.toString().endsWith(".dump"))).hasSize(1);
        }
    }

    @Test
    void retentionAlwaysKeepsNewestCopies() throws Exception {
        Files.createDirectories(backupDirectory);
        Path newest = writeDump("servisdefteri-new.dump", NOW);
        Path second = writeDump("servisdefteri-old1.dump", NOW.minusSeconds(86400L));
        Path third = writeDump("servisdefteri-old2.dump", NOW.minusSeconds(15 * 86400L));
        Path fourth = writeDump("pre-update-old3.dump", NOW.minusSeconds(16 * 86400L));

        service.cleanupOldFiles(backupDirectory);

        assertThat(newest).exists();
        assertThat(second).exists();
        assertThat(third).doesNotExist();
        assertThat(fourth).doesNotExist();
    }

    private Stream<Path> dumpFiles() throws IOException {
        if (!Files.isDirectory(backupDirectory)) {
            return Stream.empty();
        }
        return Files.list(backupDirectory).filter(path -> path.toString().endsWith(".dump"));
    }

    private Path writeDump(String name, Instant lastModified) throws IOException {
        Path file = backupDirectory.resolve(name);
        Files.writeString(file, "dump");
        Files.setLastModifiedTime(file, FileTime.from(lastModified));
        return file;
    }

    /**
     * Gerçek pg_dump yerine yarım dosya yazıp hata döndüren (veya kilitlenen)
     * bir betik üretir. Böylece zaman aşımı ve yarım dosya temizliği test edilir.
     */
    private Path successfulDumpScript() throws IOException {
        Path scriptDir = tempDir.resolve("ok-script");
        Files.createDirectories(scriptDir);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path script = scriptDir.resolve(windows ? "fake-pg-dump.cmd" : "fake-pg-dump.sh");
        String content;
        if (windows) {
            content = "@echo off\r\n"
                    + "setlocal\r\n"
                    + ":parse\r\n"
                    + "if \"%~1\"==\"\" goto ok\r\n"
                    + "if /I \"%~1\"==\"-f\" goto write\r\n"
                    + "shift\r\n"
                    + "goto parse\r\n"
                    + ":write\r\n"
                    + "> \"%~2\" echo dump\r\n"
                    + "exit /b 0\r\n"
                    + ":ok\r\n"
                    + "exit /b 0\r\n";
        } else {
            content = "#!/bin/sh\n"
                    + "while [ $# -gt 0 ]; do\n"
                    + "  if [ \"$1\" = \"-f\" ]; then\n"
                    + "    echo dump > \"$2\"\n"
                    + "    exit 0\n"
                    + "  fi\n"
                    + "  shift\n"
                    + "done\n"
                    + "exit 0\n";
        }
        Files.writeString(script, content);
        if (!windows) {
            assertThat(script.toFile().setExecutable(true)).isTrue();
        }
        return script;
    }

    private Path fakeDumpScript(boolean hang) throws IOException {        Path scriptDir = tempDir.resolve(hang ? "slow-script" : "fail-script");
        Files.createDirectories(scriptDir);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path script = scriptDir.resolve(windows ? "fake-pg-dump.cmd" : "fake-pg-dump.sh");
        String content;
        if (windows) {
            String wait = hang ? "ping -n 10 127.0.0.1 >nul\r\n" : "";
            content = "@echo off\r\n"
                    + "setlocal\r\n"
                    + ":parse\r\n"
                    + "if \"%~1\"==\"\" goto fail\r\n"
                    + "if /I \"%~1\"==\"-f\" goto write\r\n"
                    + "shift\r\n"
                    + "goto parse\r\n"
                    + ":write\r\n"
                    + "> \"%~2\" echo partial-dump\r\n"
                    + wait
                    + "exit /b 9\r\n"
                    + ":fail\r\n"
                    + "exit /b 9\r\n";
        } else {
            String wait = hang ? "sleep 10\n" : "";
            content = "#!/bin/sh\n"
                    + "while [ $# -gt 0 ]; do\n"
                    + "  if [ \"$1\" = \"-f\" ]; then\n"
                    + "    echo partial-dump > \"$2\"\n"
                    + wait
                    + "    exit 9\n"
                    + "  fi\n"
                    + "  shift\n"
                    + "done\n"
                    + "exit 9\n";
        }
        Files.writeString(script, content);
        if (!windows) {
            assertThat(script.toFile().setExecutable(true)).isTrue();
        }
        return script;
    }
}
