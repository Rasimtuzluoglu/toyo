package tr.com.servisdefteri.backup;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.update.UpdateService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * PATRON'un uygulama içinden seçtiği yedeği host ajanına geri yükleme isteği olarak iletir.
 * Gerçek geri yükleme host tarafında, uygulama durdurularak ve güvenlik yedeği alınarak yapılır.
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);
    private static final Pattern SAFE_FILE = Pattern.compile("^servisdefteri-[A-Za-z0-9._-]+\\.dump$");
    private static final String CONFIRMATION = "GERI YUKLE";

    private final BackupService backupService;
    private final UpdateService updateService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Path updatesDirectory;
    private final Clock clock;

    public RestoreService(BackupService backupService,
                          UpdateService updateService,
                          AuditService auditService,
                          ObjectMapper objectMapper,
                          Clock clock,
                          @Value("${app.update.directory:./updates}") String updatesDirectory) {
        this.backupService = backupService;
        this.updateService = updateService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.updatesDirectory = Paths.get(updatesDirectory).toAbsolutePath().normalize();
    }

    public List<BackupRun> restorableRuns() {
        return backupService.recentRuns().stream().filter(this::isRestorable).toList();
    }

    public boolean isAgentOnline() {
        return updateService.info().isAgentOnline();
    }

    public Map<String, Object> status() {
        Path statusFile = updatesDirectory.resolve("restore.status");
        if (!Files.isRegularFile(statusFile)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(statusFile.toFile(), Map.class);
        } catch (RuntimeException ex) {
            log.warn("Geri yükleme durumu okunamadı: {}", ex.getMessage());
            return Map.of();
        }
    }

    public synchronized void requestRestore(Long runId, String confirmation, String requestedBy) {
        if (confirmation == null || !CONFIRMATION.equals(confirmation.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new BusinessRuleException("Geri yükleme için onay alanına " + CONFIRMATION + " yazmalısınız.");
        }
        BackupRun run = backupService.recentRuns().stream()
                .filter(item -> item.getId().equals(runId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Yedek kaydı bulunamadı."));
        if (!isRestorable(run)) {
            throw new BusinessRuleException("Yalnızca başarılı ve dosyası mevcut yedekler geri yüklenebilir.");
        }
        if (!isAgentOnline()) {
            throw new BusinessRuleException("Geri yükleme ajanı çevrimiçi değil. "
                    + "Sunucuda güncelleme ajanını başlatın.");
        }
        if (Files.exists(updatesDirectory.resolve("update.request"))
                || Files.exists(updatesDirectory.resolve("restore.request"))) {
            throw new BusinessRuleException("Bekleyen bir güncelleme veya geri yükleme isteği var. "
                    + "Önce onun tamamlanmasını bekleyin.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("backupFile", run.getFileName());
        payload.put("relativeDirectory", relativeDirectory(run));
        payload.put("requestedBy", requestedBy == null ? "patron" : requestedBy);
        payload.put("requestedAt", LocalDateTime.now(clock).withNano(0).toString());
        try {
            Files.createDirectories(updatesDirectory);
            Path requestFile = updatesDirectory.resolve("restore.request");
            Path tempFile = updatesDirectory.resolve("restore.request.tmp");
            objectMapper.writeValue(tempFile.toFile(), payload);
            try {
                Files.move(tempFile, requestFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, requestFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("Geri yükleme isteği yazılamadı: " + ex.getMessage());
        }
        auditService.record(AuditAction.BACKUP_RESTORE_REQUESTED, "Yedek", run.getId(),
                "Geri yükleme istendi: " + run.getFileName() + ", isteyen=" + payload.get("requestedBy"));
    }

    private boolean isRestorable(BackupRun run) {
        if (run.getStatus() != BackupStatus.BASARILI || run.getFileName() == null
                || !SAFE_FILE.matcher(run.getFileName()).matches()) {
            return false;
        }
        try {
            Path directory = runDirectory(run);
            Path file = directory.resolve(run.getFileName()).normalize();
            return file.startsWith(directory) && Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (RuntimeException | IOException ex) {
            return false;
        }
    }

    private String relativeDirectory(BackupRun run) {
        Path root = Paths.get(backupService.getProperties().getDirectory()).toAbsolutePath().normalize();
        Path directory = runDirectory(run);
        if (directory.equals(root)) {
            return "";
        }
        if (!directory.startsWith(root)) {
            throw new BusinessRuleException("Bu yedek kök yedek klasörünün dışında; geri yüklenemez.");
        }
        return root.relativize(directory).toString().replace('\\', '/');
    }

    private Path runDirectory(BackupRun run) {
        String value = run.getDirectory();
        if (value == null || value.isBlank()) {
            return Paths.get(backupService.getProperties().getDirectory()).toAbsolutePath().normalize();
        }
        return Paths.get(value).toAbsolutePath().normalize();
    }
}
