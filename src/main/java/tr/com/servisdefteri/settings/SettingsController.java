package tr.com.servisdefteri.settings;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditLog;
import tr.com.servisdefteri.audit.AuditLogRepository;
import tr.com.servisdefteri.backup.BackupRun;
import tr.com.servisdefteri.backup.BackupService;
import tr.com.servisdefteri.backup.BackupStatus;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.FormTokenService;

import java.util.Locale;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final BackupService backupService;
    private final AuditLogRepository auditLogRepository;
    private final FormTokenService formTokenService;
    private final BusinessSettingsService businessSettingsService;

    public SettingsController(BackupService backupService, AuditLogRepository auditLogRepository,
                              FormTokenService formTokenService,
                              BusinessSettingsService businessSettingsService) {
        this.backupService = backupService;
        this.auditLogRepository = auditLogRepository;
        this.formTokenService = formTokenService;
        this.businessSettingsService = businessSettingsService;
    }

    @GetMapping
    public String settings(HttpSession session, Model model) {
        populateSettings(model, session);
        return "settings/index";
    }

    @PostMapping("/business")
    public String updateBusiness(@Valid @ModelAttribute("businessForm") BusinessSettingsForm form,
                                 BindingResult bindingResult,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateSettings(model, session);
            return "settings/index";
        }
        try {
            businessSettingsService.update(form);
        } catch (BusinessRuleException ex) {
            bindingResult.reject("business.failed", ex.getMessage());
            populateSettings(model, session);
            return "settings/index";
        }
        redirectAttributes.addFlashAttribute("successMessage", "İşletme bilgileri güncellendi.");
        return "redirect:/settings";
    }

    @PostMapping("/backup")
    public String runBackup(@RequestParam(name = "formToken", required = false) String formToken,
                            @RequestParam(name = "download", defaultValue = "false") boolean download,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            if (!download) {
                formTokenService.consume(session, formToken);
            }
            BackupRun run = backupService.runBackup("MANUEL");
            if (run.getStatus() == BackupStatus.BASARILI) {
                if (download) {
                    return "redirect:/settings/backups/" + run.getId() + "/download";
                }
                redirectAttributes.addFlashAttribute("successMessage",
                        "Yedekleme tamamlandı: " + run.getFileName());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Yedekleme başarısız: " + run.getMessage());
            }
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/backup-directory")
    public String updateBackupDirectory(@RequestParam(name = "directory", required = false) String directory,
                                        @RequestParam(name = "formToken", required = false) String formToken,
                                        HttpSession session,
                                        RedirectAttributes redirectAttributes) {
        try {
            formTokenService.consume(session, formToken);
            businessSettingsService.updateBackupDirectory(directory);
            redirectAttributes.addFlashAttribute("successMessage", "Yedek klasörü güncellendi.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/settings";
    }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource resource = backupService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(resource.getFilename()).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(required = false) String action,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        AuditAction actionFilter = parseAction(action);
        Page<AuditLog> logs = actionFilter == null
                ? auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), 50))
                : auditLogRepository.findByActionOrderByCreatedAtDesc(actionFilter.name(),
                PageRequest.of(Math.max(page, 0), 50));
        model.addAttribute("logs", logs);
        model.addAttribute("actions", AuditAction.values());
        model.addAttribute("action", action == null ? "" : action);
        model.addAttribute("activeMenu", "settings");
        return "settings/audit";
    }

    private void populateSettings(Model model, HttpSession session) {
        if (!model.containsAttribute("businessForm")) {
            model.addAttribute("businessForm", businessSettingsService.toForm(businessSettingsService.get()));
        }
        model.addAttribute("backupRuns", backupService.recentRuns());
        model.addAttribute("backupEnabled", backupService.isEnabled());
        model.addAttribute("backupDirectory", backupService.currentDirectory());
        model.addAttribute("retentionDays", backupService.getProperties().getRetentionDays());
        model.addAttribute("backupCron", backupService.getProperties().getCron());
        model.addAttribute("recentAudit", auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 10)).getContent());
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "settings");
    }

    private AuditAction parseAction(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
