package tr.com.servisdefteri.vehicle;

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
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.export.ExportService;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderService;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final CustomerService customerService;
    private final WorkOrderService workOrderService;
    private final FormTokenService formTokenService;
    private final ExportService exportService;

    public VehicleController(VehicleService vehicleService, CustomerService customerService,
                             WorkOrderService workOrderService, FormTokenService formTokenService,
                             ExportService exportService) {
        this.vehicleService = vehicleService;
        this.customerService = customerService;
        this.workOrderService = workOrderService;
        this.formTokenService = formTokenService;
        this.exportService = exportService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String active,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Vehicle> vehicles = vehicleService.search(q, parseBoolean(active), customerId,
                PageRequest.of(Math.max(page, 0), 20, Sort.by("plate").ascending()));
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("active", active == null ? "" : active);
        model.addAttribute("customerId", customerId);
        model.addAttribute("customers", customerService.activeCustomers());
        model.addAttribute("activeMenu", "vehicles");
        return "vehicles/list";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String active,
                                            @RequestParam(required = false) Long customerId) {
        return DownloadUtil.csv(exportService.vehiclesCsv(q, parseBoolean(active), customerId),
                "araclar.csv");
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String active,
                                              @RequestParam(required = false) Long customerId) {
        return DownloadUtil.xlsx(exportService.vehiclesExcel(q, parseBoolean(active), customerId),
                "araclar.xlsx");
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String createForm(@RequestParam(required = false) Long customerId,
                             HttpSession session, Model model) {
        VehicleForm form = new VehicleForm();
        form.setCustomerId(customerId);
        model.addAttribute("vehicleForm", form);
        model.addAttribute("customers", customerService.activeCustomers());
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "vehicles");
        return "vehicles/form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String create(@Valid @ModelAttribute("vehicleForm") VehicleForm form,
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
            model.addAttribute("customers", customerService.activeCustomers());
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "vehicles");
            return "vehicles/form";
        }
        try {
            Vehicle vehicle = vehicleService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "Araç oluşturuldu.");
            return "redirect:/vehicles/" + vehicle.getId();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("vehicle.failed", ex.getMessage());
            model.addAttribute("customers", customerService.activeCustomers());
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "vehicles");
            return "vehicles/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Vehicle vehicle = vehicleService.findById(id);
        List<WorkOrder> workOrders = workOrderService.recentByVehicle(id, 10);
        List<VehicleOwnershipView> ownership = new ArrayList<>();
        for (VehicleOwnershipHistory history : vehicleService.ownershipHistory(id)) {
            ownership.add(new VehicleOwnershipView(history.getChangedAt(),
                    customerName(history.getPreviousCustomerId()),
                    customerName(history.getNewCustomerId()),
                    history.getNote()));
        }
        model.addAttribute("vehicle", vehicle);
        model.addAttribute("workOrders", workOrders);
        model.addAttribute("ownershipHistory", ownership);
        model.addAttribute("lastServiceDate", workOrderService.lastServiceDate(id));
        model.addAttribute("activeMenu", "vehicles");
        return "vehicles/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String editForm(@PathVariable Long id, Model model) {
        Vehicle vehicle = vehicleService.findById(id);
        model.addAttribute("vehicle", vehicle);
        model.addAttribute("vehicleForm", vehicleService.toForm(vehicle));
        model.addAttribute("customers", customerService.allCustomers());
        model.addAttribute("activeMenu", "vehicles");
        return "vehicles/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("vehicleForm") VehicleForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Vehicle vehicle = vehicleService.findById(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("vehicle", vehicle);
            model.addAttribute("customers", customerService.allCustomers());
            model.addAttribute("activeMenu", "vehicles");
            return "vehicles/form";
        }
        try {
            boolean canChangeActive = principal.getRole() == Role.PATRON;
            vehicleService.update(id, form, canChangeActive);
            redirectAttributes.addFlashAttribute("successMessage", "Araç güncellendi.");
            return "redirect:/vehicles/" + id;
        } catch (BusinessRuleException ex) {
            bindingResult.reject("vehicle.failed", ex.getMessage());
            model.addAttribute("vehicle", vehicle);
            model.addAttribute("customers", customerService.allCustomers());
            model.addAttribute("activeMenu", "vehicles");
            return "vehicles/form";
        }
    }

    private String customerName(Long customerId) {
        if (customerId == null) {
            return "—";
        }
        try {
            Customer customer = customerService.findById(customerId);
            return customer.getName();
        } catch (RuntimeException ex) {
            return "Silinmiş müşteri #" + customerId;
        }
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
}
