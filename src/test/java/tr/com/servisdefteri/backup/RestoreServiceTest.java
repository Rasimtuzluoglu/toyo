package tr.com.servisdefteri.backup;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.update.UpdateInfo;
import tr.com.servisdefteri.update.UpdateService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestoreServiceTest {

    @TempDir
    Path tempDir;

    private BackupService backupService;
    private UpdateService updateService;
    private BackupRun run;
    private RestoreService service;

    @BeforeEach
    void setUp() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Path backupDirectory = backupRoot.resolve("sube-1");
        Files.createDirectories(backupDirectory);
        Files.writeString(backupDirectory.resolve("servisdefteri-20261121-020000.dump"), "dump");

        BackupProperties properties = new BackupProperties();
        properties.setDirectory(backupRoot.toString());

        run = new BackupRun();
        run.setId(7L);
        run.setStatus(BackupStatus.BASARILI);
        run.setFileName("servisdefteri-20261121-020000.dump");
        run.setDirectory(backupDirectory.toString());
        run.setStartedAt(LocalDateTime.now().minusMinutes(5));
        run.setFinishedAt(LocalDateTime.now());

        backupService = mock(BackupService.class);
        when(backupService.recentRuns()).thenReturn(List.of(run));
        when(backupService.getProperties()).thenReturn(properties);

        updateService = mock(UpdateService.class);
        UpdateInfo info = new UpdateInfo();
        info.setAgentOnline(true);
        when(updateService.info()).thenReturn(info);

        service = new RestoreService(backupService, updateService, mock(AuditService.class),
                new ObjectMapper(), Clock.systemUTC(), tempDir.resolve("updates").toString());
    }

    @Test
    void writesRestoreRequestWithRelativeBackupDirectory() throws IOException {
        service.requestRestore(7L, "geri yukle", "patron");

        Path request = tempDir.resolve("updates").resolve("restore.request");
        assertThat(request).isRegularFile();
        String payload = Files.readString(request);
        assertThat(payload)
                .contains("\"backupFile\":\"servisdefteri-20261121-020000.dump\"")
                .contains("\"relativeDirectory\":\"sube-1\"")
                .contains("\"requestedBy\":\"patron\"");
    }

    @Test
    void rejectsWrongConfirmationWithoutWritingRequest() {
        assertThatThrownBy(() -> service.requestRestore(7L, "evet", "patron"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("GERI YUKLE");

        assertThat(tempDir.resolve("updates").resolve("restore.request")).doesNotExist();
    }

    @Test
    void rejectsBackupOutsideBackupRoot() {
        Path outside = tempDir.resolve("disarida");
        try {
            Files.createDirectories(outside);
            Files.writeString(outside.resolve(run.getFileName()), "dump");
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        run.setDirectory(outside.toString());

        assertThatThrownBy(() -> service.requestRestore(7L, "GERI YUKLE", "patron"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dışında");

        assertThat(tempDir.resolve("updates").resolve("restore.request")).doesNotExist();
    }

    @Test
    void rejectsRequestWhenAgentIsOffline() {
        UpdateInfo info = new UpdateInfo();
        info.setAgentOnline(false);
        when(updateService.info()).thenReturn(info);

        assertThatThrownBy(() -> service.requestRestore(7L, "GERI YUKLE", "patron"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ajanı");
    }

    @Test
    void rejectsRequestWhenAnotherRequestIsPending() throws IOException {
        Path updates = tempDir.resolve("updates");
        Files.createDirectories(updates);
        Files.writeString(updates.resolve("update.request"), "{}");

        assertThatThrownBy(() -> service.requestRestore(7L, "GERI YUKLE", "patron"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Bekleyen");
    }

    @Test
    void rejectsFailedBackupRun() {
        run.setStatus(BackupStatus.BASARISIZ);

        assertThatThrownBy(() -> service.requestRestore(7L, "GERI YUKLE", "patron"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("başarılı");
    }

    @Test
    void rejectsUnknownBackupRun() {
        assertThatThrownBy(() -> service.requestRestore(99L, "GERI YUKLE", "patron"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("bulunamadı");
    }
}
