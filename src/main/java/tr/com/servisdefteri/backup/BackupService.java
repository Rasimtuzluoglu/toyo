package tr.com.servisdefteri.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;
import tr.com.servisdefteri.settings.BusinessSettingsService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern SAFE_FILE = Pattern.compile("^servisdefteri-[A-Za-z0-9._-]+\\.dump$");

    private final BackupRunRepository backupRunRepository;
    private final BackupProperties properties;
    private final BusinessSettingsService businessSettingsService;
    private final AuditService auditService;
    private final Clock clock;

    public BackupService(BackupRunRepository backupRunRepository, BackupProperties properties,
                         BusinessSettingsService businessSettingsService,
                         AuditService auditService, Clock clock) {
        this.backupRunRepository = backupRunRepository;
        this.properties = properties;
        this.businessSettingsService = businessSettingsService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.backup.cron:0 0 2 * * *}")
    public void scheduledBackup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            runBackup("ZAMANLANMIS");
        } catch (RuntimeException ex) {
            log.error("Zamanlanmış yedekleme başarısız oldu: {}", ex.getMessage());
        }
    }

    /**
     * Bilgisayar 02:00'de kapalıysa veya uygulama çalışmıyorsa, açılışta
     * son başarılı yedeğin üzerinden 36 saatten fazla geçmişse yedeği telafi eder.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpMissedBackup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            LocalDateTime threshold = LocalDateTime.now(clock).minusHours(36);
            boolean due = backupRunRepository
                    .findFirstByStatusOrderByStartedAtDesc(BackupStatus.BASARILI)
                    .map(run -> run.getStartedAt().isBefore(threshold))
                    .orElse(true);
            if (due) {
                log.info("Kaçırılmış yedek tespit edildi; açılış telafi yedeği alınıyor.");
                runBackup("TELAFI");
            }
        } catch (RuntimeException ex) {
            log.error("Açılış telafi yedeği alınamadı: {}", ex.getMessage());
        }
    }

    public synchronized BackupRun runBackup(String triggeredBy) {
        BackupRun run = new BackupRun();
        run.setStartedAt(LocalDateTime.now(clock));
        run.setStatus(BackupStatus.DEVAM_EDIYOR);
        run.setTriggeredBy(triggeredBy);
        backupRunRepository.save(run);

        Path target = null;
        try {
            Path directory = resolveDirectory();
            Files.createDirectories(directory);
            String fileName = "servisdefteri-" + LocalDateTime.now(clock).format(FILE_DATE) + ".dump";
            target = directory.resolve(fileName);
            run.setFileName(fileName);
            run.setDirectory(directory.toString());
            backupRunRepository.save(run);

            dump(target);
            if (!Files.exists(target) || Files.size(target) == 0) {
                throw new IllegalStateException("Yedek dosyası oluşmadı veya boş.");
            }
            verify(target);
            copyToSecondary(target);

            run.setFileSizeBytes(Files.size(target));
            run.setStatus(BackupStatus.BASARILI);
            run.setMessage("Yedekleme tamamlandı.");
            run.setFinishedAt(LocalDateTime.now(clock));
            backupRunRepository.save(run);
            auditService.record(AuditAction.BACKUP_COMPLETED, "Yedek", run.getId(),
                    "Yedek alındı: " + fileName + ", boyut=" + Files.size(target) + " bayt");
            return run;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Yedekleme hatası: {}", ex.getMessage());
            deleteQuietly(target);
            run.setFileSizeBytes(null);
            run.setStatus(BackupStatus.BASARISIZ);
            run.setFinishedAt(LocalDateTime.now(clock));
            run.setMessage(limit(ex.getMessage(), 2000));
            backupRunRepository.save(run);
            auditService.record(AuditAction.BACKUP_FAILED, "Yedek", run.getId(),
                    "Yedekleme başarısız: " + limit(ex.getMessage(), 500));
            return run;
        } finally {
            if (target != null) {
                cleanupOldFiles(target.getParent());
            }
        }
    }

    /**
     * pg_dump çıktısını ayrı bir iş parçacığında okur; böylece süreç kilitlenirse
     * waitFor zaman aşımı gerçekten devreye girer.
     */
    private String dump(Path target) throws IOException, InterruptedException {
        List<String> command = List.of(properties.getPgDumpPath(),
                "-h", properties.getHost(),
                "-p", String.valueOf(properties.getPort()),
                "-U", properties.getUsername(),
                "-Fc",
                "-f", target.toString(),
                properties.getDatabase());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", properties.getPassword());
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        if (output.length() < 3000) {
                            output.append(line).append(' ');
                        }
                    }
                }
            } catch (IOException ex) {
                log.debug("pg_dump çıktısı okunamadı: {}", ex.getMessage());
            }
        }, "pg-dump-output");
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            readerThread.join(2000);
            throw new IllegalStateException("Yedekleme zaman aşımına uğradı.");
        }
        readerThread.join(5000);
        String text;
        synchronized (output) {
            text = output.toString().trim();
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("pg_dump hata kodu döndürdü: " + process.exitValue()
                    + (text.isBlank() ? "" : " - " + text));
        }
        return text;
    }

    /**
     * Yedeğin gerçekten okunabilir bir PostgreSQL arşivi olduğunu doğrular.
     * Yalnızca dosya boyutuna bakmak bozuk yedeği başarılı sayar.
     */
    private void verify(Path target) throws IOException, InterruptedException {
        if (!properties.isVerify()) {
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(
                properties.getPgRestorePath(), "--list", target.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Yedek doğrulaması zaman aşımına uğradı.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Yedek dosyası doğrulanamadı (pg_restore --list hata kodu "
                    + process.exitValue() + ").");
        }
    }

    /**
     * Ana yedek başarılı olduktan sonra isteğe bağlı ikinci kopyayı (harici disk/NAS)
     * yazar. İkinci kopya başarısız olsa bile ana yedek başarılı sayılır; hata loglanır.
     */
    private void copyToSecondary(Path source) {
        String configured = properties.getCopyDirectory();
        if (configured == null || configured.isBlank()) {
            return;
        }
        try {
            Path directory = Paths.get(configured).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path copy = directory.resolve(source.getFileName());
            Files.copy(source, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Yedek ikinci kopyası yazıldı: {}", copy);
        } catch (IOException ex) {
            log.warn("Yedek ikinci kopyası yazılamadı: {}", ex.getMessage());
        }
    }

    static void deleteQuietly(Path path) {        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Yarım kalan yedek dosyası silinemedi: {}", path.getFileName());
        }
    }

    public List<BackupRun> recentRuns() {
        return backupRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 20));
    }

    public Resource download(Long runId) {
        BackupRun run = backupRunRepository.findById(runId)
                .orElseThrow(() -> NotFoundException.of("Yedek", runId));
        if (run.getFileName() == null || !SAFE_FILE.matcher(run.getFileName()).matches()
                || run.getStatus() != BackupStatus.BASARILI) {
            throw new BusinessRuleException("Bu yedek dosyası indirilemez.");
        }
        Path directory = run.getDirectory() == null || run.getDirectory().isBlank()
                ? resolveDirectory() : Paths.get(run.getDirectory()).toAbsolutePath().normalize();
        Path file = directory.resolve(run.getFileName()).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            throw new BusinessRuleException("Yedek dosyası bulunamadı.");
        }
        return new FileSystemResource(file);
    }

    public String currentDirectory() {
        return resolveDirectory().toString();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public BackupProperties getProperties() {
        return properties;
    }

    void cleanupOldFiles(Path directory) {
        if (properties.getRetentionDays() <= 0 || directory == null) {
            return;
        }
        Instant threshold = LocalDateTime.now(clock).minusDays(properties.getRetentionDays())
                .atZone(clock.getZone()).toInstant();
        int keep = Math.max(properties.getMinCopies(), 0);
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> candidates = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return (name.startsWith("servisdefteri-") || name.startsWith("pre-update-"))
                                && name.endsWith(".dump");
                    })
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .toList();
            for (int i = keep; i < candidates.size(); i++) {
                Path path = candidates.get(i);
                if (lastModified(path).isBefore(threshold)) {
                    deleteQuietly(path);
                    log.info("Saklama süresi dolan yedek silindi: {}", path.getFileName());
                }
            }
        } catch (IOException ex) {
            log.warn("Eski yedekler temizlenemedi: {}", ex.getMessage());
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private Path resolveDirectory() {
        String configured = businessSettingsService.get().getBackupDirectory();
        String directory = configured == null || configured.isBlank()
                ? properties.getDirectory() : configured;
        return Paths.get(directory).toAbsolutePath().normalize();
    }
}
