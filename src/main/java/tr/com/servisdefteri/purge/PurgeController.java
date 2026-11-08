package tr.com.servisdefteri.purge;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequestMapping("/purge")
@PreAuthorize("hasRole('PATRON')")
public class PurgeController {

    private final PurgeService purgeService;

    public PurgeController(PurgeService purgeService) {
        this.purgeService = purgeService;
    }

    @GetMapping("/customer/{id}")
    public String customer(@PathVariable Long id, Model model) {
        return confirm(purgeService.summaryCustomer(id), model);
    }

    @GetMapping("/vehicle/{id}")
    public String vehicle(@PathVariable Long id, Model model) {
        return confirm(purgeService.summaryVehicle(id), model);
    }

    @GetMapping("/work-order/{id}")
    public String workOrder(@PathVariable Long id, Model model) {
        return confirm(purgeService.summaryWorkOrder(id), model);
    }

    @GetMapping("/payment/{id}")
    public String payment(@PathVariable Long id, Model model) {
        return confirm(purgeService.summaryPayment(id), model);
    }

    @PostMapping("/customer/{id}")
    public String purgeCustomer(@PathVariable Long id,
                                @RequestParam(required = false) String confirmation,
                                Model model, RedirectAttributes redirectAttributes) {
        PurgeSummary summary = purgeService.summaryCustomer(id);
        if (!matches(confirmation, summary.reference())) {
            return mismatch(summary, model);
        }
        return purge("customer", id, summary, model, redirectAttributes);
    }

    @PostMapping("/vehicle/{id}")
    public String purgeVehicle(@PathVariable Long id,
                               @RequestParam(required = false) String confirmation,
                               Model model, RedirectAttributes redirectAttributes) {
        PurgeSummary summary = purgeService.summaryVehicle(id);
        if (!matches(confirmation, summary.reference())) {
            return mismatch(summary, model);
        }
        return purge("vehicle", id, summary, model, redirectAttributes);
    }

    @PostMapping("/work-order/{id}")
    public String purgeWorkOrder(@PathVariable Long id,
                                 @RequestParam(required = false) String confirmation,
                                 Model model, RedirectAttributes redirectAttributes) {
        PurgeSummary summary = purgeService.summaryWorkOrder(id);
        if (!matches(confirmation, summary.reference())) {
            return mismatch(summary, model);
        }
        return purge("work-order", id, summary, model, redirectAttributes);
    }

    @PostMapping("/payment/{id}")
    public String purgePayment(@PathVariable Long id,
                               @RequestParam(required = false) String confirmation,
                               Model model, RedirectAttributes redirectAttributes) {
        PurgeSummary summary = purgeService.summaryPayment(id);
        if (!matches(confirmation, summary.reference())) {
            return mismatch(summary, model);
        }
        return purge("payment", id, summary, model, redirectAttributes);
    }

    private String purge(String kind, Long id, PurgeSummary summary, Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            switch (kind) {
                case "customer" -> purgeService.purgeCustomer(id);
                case "vehicle" -> purgeService.purgeVehicle(id);
                case "work-order" -> purgeService.purgeWorkOrder(id);
                case "payment" -> purgeService.purgePayment(id);
                default -> throw new IllegalArgumentException("Bilinmeyen silme türü.");
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    summary.reference() + " kalıcı olarak silindi.");
            return redirect(kind);
        } catch (RuntimeException ex) {
            model.addAttribute("errorMessage", "Silme tamamlanamadı: " + ex.getMessage());
            return confirm(summary, model);
        }
    }

    private String mismatch(PurgeSummary summary, Model model) {
        model.addAttribute("confirmationError",
                "Onay için \"" + summary.reference() + "\" yazmalısınız.");
        return confirm(summary, model);
    }

    private String confirm(PurgeSummary summary, Model model) {
        model.addAttribute("summary", summary);
        model.addAttribute("backUrl", switch (summary.kind()) {
            case "customer" -> "/customers/" + summary.id();
            case "vehicle" -> "/vehicles/" + summary.id();
            case "work-order" -> "/work-orders/" + summary.id();
            default -> "/payments/" + summary.id();
        });
        model.addAttribute("activeMenu", switch (summary.kind()) {
            case "customer" -> "customers";
            case "vehicle" -> "vehicles";
            case "work-order" -> "workorders";
            default -> "payments";
        });
        return "purge/confirm";
    }

    private String redirect(String kind) {
        return switch (kind) {
            case "customer" -> "redirect:/customers?active=all";
            case "vehicle" -> "redirect:/vehicles?active=all";
            case "work-order" -> "redirect:/work-orders";
            default -> "redirect:/payments";
        };
    }

    private boolean matches(String confirmation, String reference) {
        if (confirmation == null || reference == null) {
            return false;
        }
        Locale turkish = Locale.forLanguageTag("tr-TR");
        return confirmation.strip().toLowerCase(turkish)
                .equals(reference.strip().toLowerCase(turkish));
    }
}
