package tr.com.servisdefteri.payment;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.OpenDebt;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.export.ExportService;
import tr.com.servisdefteri.pdf.PdfService;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.settings.BusinessSettingsService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final BusinessSettingsService businessSettingsService;
    private final FormTokenService formTokenService;
    private final Clock clock;
    private final ExportService exportService;
    private final PdfService pdfService;

    public PaymentController(PaymentService paymentService, CustomerService customerService,
                             AccountService accountService, BusinessSettingsService businessSettingsService,
                             FormTokenService formTokenService, Clock clock, ExportService exportService,
                             PdfService pdfService) {
        this.paymentService = paymentService;
        this.customerService = customerService;
        this.accountService = accountService;
        this.businessSettingsService = businessSettingsService;
        this.formTokenService = formTokenService;
        this.clock = clock;
        this.exportService = exportService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) String method,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Payment> payments = paymentService.search(customerId, parseMethod(method), parseStatus(status),
                start, end, q, Math.max(page, 0), 20);
        model.addAttribute("payments", payments);
        model.addAttribute("customers", customerService.allCustomers());
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("statuses", PaymentStatus.values());
        model.addAttribute("customerId", customerId);
        model.addAttribute("method", method == null ? "" : method);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("activeMenu", "payments");
        return "payments/list";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Long customerId,
                                            @RequestParam(required = false) String method,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                            @RequestParam(required = false) String q) {
        return DownloadUtil.csv(exportService.paymentsCsv(customerId, parseMethod(method),
                parseStatus(status), start, end, q), "tahsilatlar.csv");
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) String method,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                              @RequestParam(required = false) String q) {
        return DownloadUtil.xlsx(exportService.paymentsExcel(customerId, parseMethod(method),
                parseStatus(status), start, end, q), "tahsilatlar.xlsx");
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long customerId,
                             HttpSession session, Model model) {
        PaymentForm form = new PaymentForm();
        form.setCustomerId(customerId);
        form.setPaymentDate(LocalDate.now(clock));
        model.addAttribute("paymentForm", form);
        prepare(form, model);
        applySingleDebtDefaults(form);
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "payments");
        return "payments/form";
    }

    /**
     * Tek açık borç varsa tutarı ve dağıtımı hazırlar; kullanıcı yalnızca Kaydet'e basar.
     */
    private void applySingleDebtDefaults(PaymentForm form) {
        if (form.getCustomerId() == null || form.getAmount() != null) {
            return;
        }
        List<OpenDebt> debts = paymentService.openDebts(form.getCustomerId());
        if (debts.size() != 1) {
            return;
        }
        BigDecimal remaining = debts.get(0).getRemaining();
        if (remaining == null || remaining.signum() <= 0) {
            return;
        }
        form.setAmount(remaining);
        if (form.getAllocations().isEmpty()) {
            AllocationForm allocation = new AllocationForm();
            allocation.setTransactionId(debts.get(0).id());
            form.getAllocations().add(allocation);
        }
        form.getAllocations().get(0).setAmount(remaining);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("paymentForm") PaymentForm form,
                         BindingResult bindingResult,
                         @RequestParam(name = "formToken", required = false) String formToken,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            formTokenService.consume(session, formToken);
        } catch (BusinessRuleException ex) {
            bindingResult.reject("form.token", ex.getMessage());
        }
        if (bindingResult.hasErrors()) {
            prepare(form, model);
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "payments");
            return "payments/form";
        }
        try {
            Payment payment = paymentService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Tahsilat kaydedildi.");
            return "redirect:/payments/" + payment.getId();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("payment.failed", ex.getMessage());
            prepare(form, model);
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "payments");
            return "payments/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        Payment payment = paymentService.findById(id);
        model.addAttribute("payment", payment);
        model.addAttribute("allocations", paymentService.allocations(id));
        model.addAttribute("balance", accountService.balance(payment.getCustomer().getId()));
        model.addAttribute("openDebtTotal", accountService.openDebtTotal(payment.getCustomer().getId()));
        model.addAttribute("canCancel", principal.getRole() == Role.PATRON
                || principal.getRole() == Role.MUHASEBE);
        model.addAttribute("activeMenu", "payments");
        return "payments/detail";
    }

    @GetMapping("/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        Payment payment = paymentService.findById(id);
        model.addAttribute("payment", payment);
        model.addAttribute("allocations", paymentService.allocations(id));
        model.addAttribute("business", businessSettingsService.get());
        return "payments/receipt";
    }

    @GetMapping("/{id}/receipt.pdf")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long id) {
        Payment payment = paymentService.findById(id);
        return DownloadUtil.pdf(pdfService.receiptPdf(payment),
                "makbuz-" + payment.getReceiptNo() + ".pdf");
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            paymentService.cancel(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tahsilat iptal edildi. Ters kayıt cari hesaba işlendi.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/payments/" + id;
    }

    private void prepare(PaymentForm form, Model model) {
        model.addAttribute("customers", customerService.allCustomers());
        model.addAttribute("methods", PaymentMethod.values());
        if (form.getPaymentDate() == null) {
            form.setPaymentDate(LocalDate.now(clock));
        }
        if (form.getAllocations() == null) {
            form.setAllocations(new ArrayList<>());
        }
        if (form.getCustomerId() == null) {
            return;
        }
        List<OpenDebt> debts = paymentService.openDebts(form.getCustomerId());
        Map<Long, OpenDebt> byId = new LinkedHashMap<>();
        for (OpenDebt debt : debts) {
            byId.put(debt.id(), debt);
        }
        if (form.getAllocations().isEmpty()) {
            for (OpenDebt debt : debts) {
                AllocationForm allocation = new AllocationForm();
                allocation.setTransactionId(debt.id());
                form.getAllocations().add(allocation);
            }
        }
        List<AllocationView> views = new ArrayList<>();
        for (int i = 0; i < form.getAllocations().size(); i++) {
            AllocationForm allocation = form.getAllocations().get(i);
            if (allocation == null || allocation.getTransactionId() == null) {
                continue;
            }
            OpenDebt debt = byId.get(allocation.getTransactionId());
            if (debt != null) {
                views.add(new AllocationView(i, debt));
            }
        }
        BigDecimal remaining = debts.stream().map(OpenDebt::getRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("allocationViews", views);
        model.addAttribute("openDebtTotal", remaining);
        model.addAttribute("selectedCustomer", customerService.findById(form.getCustomerId()));
    }

    private PaymentMethod parseMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PaymentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
