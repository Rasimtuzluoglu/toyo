package tr.com.servisdefteri.customer;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.export.ExportService;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderService;

import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final WorkOrderService workOrderService;
    private final AccountService accountService;
    private final FormTokenService formTokenService;
    private final ExportService exportService;

    public CustomerController(CustomerService customerService, VehicleService vehicleService,
                              WorkOrderService workOrderService, AccountService accountService,
                              FormTokenService formTokenService, ExportService exportService) {
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.workOrderService = workOrderService;
        this.accountService = accountService;
        this.formTokenService = formTokenService;
        this.exportService = exportService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String active,
                       @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        String activeValue = normalizeActiveFilter(active);
        Boolean activeFilter = "all".equals(activeValue) ? null : Boolean.valueOf(activeValue);
        CustomerType typeFilter = parseType(type);
        Page<Customer> customers = customerService.search(q, activeFilter, typeFilter,
                PageRequest.of(Math.max(page, 0), 20, Sort.by("name").ascending()));
        model.addAttribute("customers", customers);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("active", activeValue);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("types", CustomerType.values());
        model.addAttribute("activeMenu", "customers");
        return "customers/list";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('PATRON')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            customerService.deactivate(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Müşteri silindi. Geçmiş iş emri ve finans kayıtları korunuyor.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/customers?active=true";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String active,
                                            @RequestParam(required = false) String type) {
        return DownloadUtil.csv(exportService.customersCsv(q, parseBoolean(active), parseType(type)),
                "musteriler.csv");
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String active,
                                              @RequestParam(required = false) String type) {
        return DownloadUtil.xlsx(exportService.customersExcel(q, parseBoolean(active), parseType(type)),
                "musteriler.xlsx");
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String createForm(HttpSession session, Model model) {
        model.addAttribute("customerForm", new CustomerForm());
        model.addAttribute("types", CustomerType.values());
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "customers");
        return "customers/form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String create(@Valid @ModelAttribute("customerForm") CustomerForm form,
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
            model.addAttribute("types", CustomerType.values());
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "customers");
            return "customers/form";
        }
        try {
            Customer customer = customerService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Müşteri oluşturuldu.");
            return "redirect:/customers/" + customer.getId();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("customer.failed", ex.getMessage());
            model.addAttribute("types", CustomerType.values());
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "customers");
            return "customers/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        Customer customer = customerService.findById(id);
        List<Vehicle> vehicles = vehicleService.byCustomer(id);
        List<WorkOrder> workOrders = workOrderService.recentByCustomer(id, 10);
        boolean financial = principal.getRole() == Role.PATRON
                || principal.getRole() == Role.MUHASEBE;

        model.addAttribute("customer", customer);
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("workOrders", workOrders);
        model.addAttribute("financialVisible", financial);
        if (financial) {
            model.addAttribute("balance", accountService.balance(id));
            model.addAttribute("openDebtTotal", accountService.openDebtTotal(id));
        }
        model.addAttribute("activeMenu", "customers");
        return "customers/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String editForm(@PathVariable Long id, Model model) {
        Customer customer = customerService.findById(id);
        model.addAttribute("customer", customer);
        model.addAttribute("customerForm", customerService.toForm(customer));
        model.addAttribute("types", CustomerType.values());
        model.addAttribute("activeMenu", "customers");
        return "customers/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("customerForm") CustomerForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Customer customer = customerService.findById(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("customer", customer);
            model.addAttribute("types", CustomerType.values());
            model.addAttribute("activeMenu", "customers");
            return "customers/form";
        }
        try {
            boolean canChangeActive = principal.getRole() == Role.PATRON;
            customerService.update(id, form, canChangeActive);
            redirectAttributes.addFlashAttribute("successMessage", "Müşteri güncellendi.");
            return "redirect:/customers/" + id;
        } catch (BusinessRuleException ex) {
            bindingResult.reject("customer.failed", ex.getMessage());
            model.addAttribute("customer", customer);
            model.addAttribute("types", CustomerType.values());
            model.addAttribute("activeMenu", "customers");
            return "customers/form";
        }
    }

    private String normalizeActiveFilter(String value) {
        if ("all".equalsIgnoreCase(value)) {
            return "all";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "false";
        }
        return "true";
    }

    private Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private CustomerType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CustomerType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
