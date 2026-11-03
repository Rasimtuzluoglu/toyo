package tr.com.servisdefteri.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public BusinessSettings update(BusinessSettingsForm form) {        BusinessSettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(BusinessSettings::new);
        settings.setId(SETTINGS_ID);
        settings.setName(form.getName().trim());
        settings.setPhone(blankToNull(form.getPhone()));
        settings.setEmail(blankToNull(form.getEmail()));
        settings.setAddress(blankToNull(form.getAddress()));
        settings.setTaxOffice(blankToNull(form.getTaxOffice()));
        settings.setTaxNumber(blankToNull(form.getTaxNumber()));
        settings.setUpdatedAt(LocalDateTime.now(clock));
        settings.setUpdatedBy(currentUserService.idOrNull());
        BusinessSettings saved = repository.save(settings);
        auditService.record(AuditAction.BUSINESS_SETTINGS_UPDATED, "İşletmeAyarları", SETTINGS_ID,
                "İşletme bilgileri güncellendi: " + saved.getName());
        return saved;
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

    public BusinessSettingsForm toForm(BusinessSettings settings) {        BusinessSettingsForm form = new BusinessSettingsForm();
        form.setName(settings.getName());
        form.setPhone(settings.getPhone());
        form.setEmail(settings.getEmail());
        form.setAddress(settings.getAddress());
        form.setTaxOffice(settings.getTaxOffice());
        form.setTaxNumber(settings.getTaxNumber());
        return form;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
