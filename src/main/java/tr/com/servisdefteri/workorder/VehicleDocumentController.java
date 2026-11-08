package tr.com.servisdefteri.workorder;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.pdf.PdfService;
import tr.com.servisdefteri.settings.BusinessSettingsService;

@Controller
@RequestMapping("/work-orders/{orderId}")
public class VehicleDocumentController {

    private final WorkOrderService workOrderService;
    private final VehicleDocumentService documentService;
    private final BusinessSettingsService businessSettingsService;
    private final PdfService pdfService;
    private final AccountService accountService;

    public VehicleDocumentController(WorkOrderService workOrderService,
                                     VehicleDocumentService documentService,
                                     BusinessSettingsService businessSettingsService,
                                     PdfService pdfService,
                                     AccountService accountService) {
        this.workOrderService = workOrderService;
        this.documentService = documentService;
        this.businessSettingsService = businessSettingsService;
        this.pdfService = pdfService;
        this.accountService = accountService;
    }

    @GetMapping("/intake")
    public String intakeForm(@PathVariable Long orderId, Model model) {
        WorkOrder order = workOrderService.findById(orderId);
        VehicleIntake intake = documentService.findIntake(orderId);
        model.addAttribute("order", order);
        model.addAttribute("intake", intake);
        model.addAttribute("intakeForm", documentService.toForm(intake));
        model.addAttribute("fuelLevels", FuelLevel.values());
        model.addAttribute("activeMenu", "workorders");
        return "workorders/intake";
    }

    @PostMapping("/intake")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String saveIntake(@PathVariable Long orderId,
                             @Valid @ModelAttribute("intakeForm") VehicleIntakeForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateIntakeModel(orderId, model);
            return "workorders/intake";
        }
        try {
            documentService.saveIntake(orderId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Araç kabul formu kaydedildi.");
            return "redirect:/work-orders/" + orderId;
        } catch (BusinessRuleException ex) {
            bindingResult.reject("intake.failed", ex.getMessage());
            populateIntakeModel(orderId, model);
            return "workorders/intake";
        }
    }

    @GetMapping("/intake/print")
    public String intakePrint(@PathVariable Long orderId, Model model, RedirectAttributes redirectAttributes) {
        WorkOrder order = workOrderService.findById(orderId);
        VehicleIntake intake = documentService.findIntake(orderId);
        if (intake == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Önce araç kabul formunu kaydedin.");
            return "redirect:/work-orders/" + orderId + "/intake";
        }
        model.addAttribute("order", order);
        model.addAttribute("intake", intake);
        model.addAttribute("business", businessSettingsService.get());
        return "workorders/intake-print";
    }

    @GetMapping("/intake.pdf")
    public ResponseEntity<byte[]> intakePdf(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
        WorkOrder order = workOrderService.findById(orderId);
        VehicleIntake intake = documentService.findIntake(orderId);
        if (intake == null) {
            return ResponseEntity.notFound().build();
        }
        return DownloadUtil.pdf(pdfService.intakePdf(order, intake),
                "arac-kabul-" + order.getOrderNo() + ".pdf");
    }

    @GetMapping("/delivery")
    public String deliveryForm(@PathVariable Long orderId, Model model) {
        WorkOrder order = workOrderService.findById(orderId);
        VehicleDelivery delivery = documentService.findDelivery(orderId);
        model.addAttribute("order", order);
        model.addAttribute("delivery", delivery);
        model.addAttribute("deliveryForm", documentService.toForm(delivery, order));
        model.addAttribute("fuelLevels", FuelLevel.values());
        model.addAttribute("customerBalance", accountService.balance(order.getCustomer().getId()));
        model.addAttribute("workOrderDebt", accountService.workOrderDebt(orderId));
        model.addAttribute("activeMenu", "workorders");
        return "workorders/delivery";
    }

    @PostMapping("/delivery")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String saveDelivery(@PathVariable Long orderId,
                               @Valid @ModelAttribute("deliveryForm") VehicleDeliveryForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateDeliveryModel(orderId, model);
            return "workorders/delivery";
        }
        try {
            documentService.saveDelivery(orderId, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Teslim tutanağı kaydedildi; iş emri teslim edildi.");
            return "redirect:/work-orders/" + orderId;
        } catch (BusinessRuleException ex) {
            bindingResult.reject("delivery.failed", ex.getMessage());
            populateDeliveryModel(orderId, model);
            return "workorders/delivery";
        }
    }

    @GetMapping("/delivery/print")
    public String deliveryPrint(@PathVariable Long orderId, Model model, RedirectAttributes redirectAttributes) {
        WorkOrder order = workOrderService.findById(orderId);
        VehicleDelivery delivery = documentService.findDelivery(orderId);
        if (delivery == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Önce teslim tutanağını kaydedin.");
            return "redirect:/work-orders/" + orderId + "/delivery";
        }
        model.addAttribute("order", order);
        model.addAttribute("delivery", delivery);
        model.addAttribute("business", businessSettingsService.get());
        return "workorders/delivery-print";
    }

    @GetMapping("/delivery.pdf")
    public ResponseEntity<byte[]> deliveryPdf(@PathVariable Long orderId) {
        WorkOrder order = workOrderService.findById(orderId);
        VehicleDelivery delivery = documentService.findDelivery(orderId);
        if (delivery == null) {
            return ResponseEntity.notFound().build();
        }
        return DownloadUtil.pdf(pdfService.deliveryPdf(order, delivery),
                "teslim-tutanagi-" + order.getOrderNo() + ".pdf");
    }

    private void populateIntakeModel(Long orderId, Model model) {
        model.addAttribute("order", workOrderService.findById(orderId));
        model.addAttribute("intake", documentService.findIntake(orderId));
        model.addAttribute("fuelLevels", FuelLevel.values());
        model.addAttribute("activeMenu", "workorders");
    }

    private void populateDeliveryModel(Long orderId, Model model) {
        WorkOrder order = workOrderService.findById(orderId);
        model.addAttribute("order", order);
        model.addAttribute("delivery", documentService.findDelivery(orderId));
        model.addAttribute("fuelLevels", FuelLevel.values());
        model.addAttribute("customerBalance", accountService.balance(order.getCustomer().getId()));
        model.addAttribute("workOrderDebt", accountService.workOrderDebt(orderId));
        model.addAttribute("activeMenu", "workorders");
    }
}
