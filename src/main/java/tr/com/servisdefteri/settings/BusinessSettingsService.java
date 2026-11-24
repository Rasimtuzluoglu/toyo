package tr.com.servisdefteri.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.security.CurrentUserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class BusinessSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final BusinessSettingsRepository repository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Value("${app.backup.directory:./backups}")
    private String backupRootDirectory;

    public BusinessSettingsService(BusinessSettingsRepository repository, AuditService auditService,
                                   CurrentUserService currentUserService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BusinessSettings get() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> {
            BusinessSettings settings = new BusinessSettings();
            settings.setId(SETTINGS_ID);
            settings.setName("Toyota Plaza Öztoprak");
            return settings;
        });
    }

    @Transactional
    public BusinessSettings update(BusinessSettingsForm form) {
        BusinessSettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(BusinessSettings::new);
        settings.setId(SETTINGS_ID);
        settings.setName(form.getName().trim());
        settings.setPhone(blankToNull(form.getPhone()));
        settings.setEmail(blankToNull(form.getEmail()));
        settings.setAddress(blankToNull(form.getAddress()));
        settings.setTaxOffice(blankToNull(form.getTaxOffice()));
        settings.setTaxNumber(blankToNull(form.getTaxNumber()));
        settings.setTagline(form.getTagline() == null || form.getTagline().isBlank()
                ? "Yetkili Servis ve Bakım" : form.getTagline().trim());
        settings.setPrimaryColor(form.getPrimaryColor() == null || form.getPrimaryColor().isBlank()
                ? "#eb0a1e" : form.getPrimaryColor());
        applyLogo(settings, form);
        settings.setUpdatedAt(LocalDateTime.now(clock));
        settings.setUpdatedBy(currentUserService.idOrNull());
        BusinessSettings saved = repository.save(settings);
        auditService.record(AuditAction.BUSINESS_SETTINGS_UPDATED, "İşletmeAyarları", SETTINGS_ID,
                "İşletme bilgileri güncellendi: " + saved.getName());
        return saved;
    }

    private void applyLogo(BusinessSettings settings, BusinessSettingsForm form) {
        if (form.isRemoveLogo()) {
            settings.setLogoData(null);
            settings.setLogoContentType(null);
            return;
        }
        if (form.getLogo() == null || form.getLogo().isEmpty()) {
            return;
        }
        String contentType = form.getLogo().getContentType();
        if (contentType == null || !(contentType.equals("image/png")
                || contentType.equals("image/jpeg") || contentType.equals("image/webp"))) {
            throw new BusinessRuleException("Logo PNG, JPEG veya WebP olmalıdır.");
        }
        if (form.getLogo().getSize() > 2 * 1024 * 1024) {
            throw new BusinessRuleException("Logo boyutu en fazla 2 MB olabilir.");
        }
        try {
            byte[] bytes = form.getLogo().getBytes();
            if (!matchesImageSignature(bytes, contentType)) {
                throw new BusinessRuleException("Logo dosyasının içeriği seçilen görsel türüyle uyuşmuyor.");
            }
            settings.setLogoData(bytes);
            settings.setLogoContentType(contentType);
        } catch (IOException ex) {
            throw new BusinessRuleException("Logo okunamadı: " + ex.getMessage());
        }
    }

    private boolean matchesImageSignature(byte[] data, String contentType) {
        if ("image/png".equals(contentType)) {
            return data.length >= 8
                    && (data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
                    && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A;
        }
        if ("image/jpeg".equals(contentType)) {
            return data.length >= 3
                    && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
        }
        if ("image/webp".equals(contentType)) {
            return data.length >= 12
                    && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
        }
        return false;
    }

    @Transactional
    public void updateBackupDirectory(String directory) {
        String normalized = null;
        if (directory != null && !directory.isBlank()) {
            Path path = Paths.get(directory.trim()).toAbsolutePath().normalize();
            try {
                Files.createDirectories(path);
                if (!Files.isDirectory(path) || !Files.isWritable(path)) {
                    throw new IOException("dizin yazılabilir değil");
                }
            } catch (IOException ex) {
                throw new BusinessRuleException("Yedek klasörü kullanılamıyor: " + ex.getMessage());
            }
            normalized = path.toString();
        }
        saveBackupDirectory(normalized);
    }

    /**
     * PATRON'un uygulama içinden yedek kökü altında alt klasör seçmesini sağlar.
     * Kök dışına çıkan yollar (../, mutlak yol) reddedilir.
     */
    @Transactional
    public void updateBackupSubdirectory(String subdirectory) {
        if (subdirectory == null || subdirectory.isBlank()) {
            saveBackupDirectory(null);
            return;
        }
        Path base = Paths.get(backupRootDirectory).toAbsolutePath().normalize();
        Path target = base;
        String relative = subdirectory.trim().replace('\\', '/');
        if (relative.startsWith("/") || relative.contains("..") || relative.contains(":")) {
            throw new BusinessRuleException("Yedek alt klasörü geçersiz.");
        }
        String[] segments = relative.split("/");
        if (segments.length > 3) {
            throw new BusinessRuleException("Yedek alt klasörü en fazla 3 seviye olabilir.");
        }
        for (String segment : segments) {
            if (!segment.matches("[A-Za-z0-9._-]{1,50}")) {
                throw new BusinessRuleException(
                        "Klasör adı yalnızca harf, rakam, nokta, tire ve alt çizgi içerebilir.");
            }
            target = target.resolve(segment);
        }
        target = target.normalize();
        if (!target.startsWith(base)) {
            throw new BusinessRuleException("Yedek alt klasörü kök klasörün dışına çıkamaz.");
        }
        try {
            Files.createDirectories(target);
            if (!Files.isDirectory(target) || !Files.isWritable(target)) {
                throw new IOException("dizin yazılabilir değil");
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("Yedek klasörü kullanılamıyor: " + ex.getMessage());
        }
        saveBackupDirectory(target.toString());
    }

    private void saveBackupDirectory(String normalized) {
        BusinessSettings settings = repository.findById(SETTINGS_ID).orElseGet(() -> {
            BusinessSettings created = new BusinessSettings();
            created.setId(SETTINGS_ID);
            created.setName("Toyota Plaza Öztoprak");
            return created;
        });
        settings.setBackupDirectory(normalized);
        settings.setUpdatedAt(LocalDateTime.now(clock));
        settings.setUpdatedBy(currentUserService.idOrNull());
        repository.save(settings);
        auditService.record(AuditAction.BUSINESS_SETTINGS_UPDATED, "İşletmeAyarları", SETTINGS_ID,
                "Yedek klasörü güncellendi: " + (normalized == null ? "varsayılan" : normalized));
    }

    public BusinessSettingsForm toForm(BusinessSettings settings) {
        BusinessSettingsForm form = new BusinessSettingsForm();
        form.setName(settings.getName());
        form.setPhone(settings.getPhone());
        form.setEmail(settings.getEmail());
        form.setAddress(settings.getAddress());
        form.setTaxOffice(settings.getTaxOffice());
        form.setTaxNumber(settings.getTaxNumber());
        form.setTagline(settings.getTagline());
        form.setPrimaryColor(settings.getPrimaryColor());
        return form;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
