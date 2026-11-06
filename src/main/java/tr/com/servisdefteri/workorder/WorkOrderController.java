package tr.com.servisdefteri.workorder;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
import tr.com.servisdefteri.common.CsvUtil;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.common.SearchNormalizer;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.export.ExportService;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.pdf.PdfService;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.security.UserService;
import tr.com.servisdefteri.settings.BusinessSettingsService;
import tr.com.servisdefteri.vehicle.VehicleService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final UserService userService;
    private final BusinessSettingsService businessSettingsService;
    private final FormTokenService formTokenService;
    private final Clock clock;
    private final ExportService exportService;
    private final QuickWorkOrderService quickWorkOrderService;
    private final WorkOrderItemTemplateRepository itemTemplateRepository;
    private final AccountService accountService;
    private final PdfService pdfService;
    private final VehicleDocumentService documentService;

    public WorkOrderController(WorkOrderService workOrderService, CustomerService customerService,
                               VehicleService vehicleService, UserService userService,
                               BusinessSettingsService businessSettingsService,
                               FormTokenService formTokenService, Clock clock,
                               ExportService exportService,
                               QuickWorkOrderService quickWorkOrderService,
                               WorkOrderItemTemplateRepository itemTemplateRepository,
                               AccountService accountService,
                               PdfService pdfService,
                               VehicleDocumentService documentService) {
        this.workOrderService = workOrderService;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.userService = userService;
        this.businessSettingsService = businessSettingsService;
        this.formTokenService = formTokenService;
        this.clock = clock;
        this.exportService = exportService;
        this.quickWorkOrderService = quickWorkOrderService;
        this.itemTemplateRepository = itemTemplateRepository;
        this.accountService = accountService;
        this.pdfService = pdfService;
        this.documentService = documentService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) Long vehicleId,
                       @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso =
                               org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate start,
                       @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso =
                               org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate end,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        WorkOrderStatus statusFilter = parseStatus(status);
        Page<WorkOrder> orders = workOrderService.search(statusFilter, customerId, vehicleId, start, end, q,
                Math.max(page, 0), 20);
        model.addAttribute("orders", orders);
        model.addAttribute("statuses", WorkOrderStatus.values());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("customerId", customerId);
        model.addAttribute("vehicleId", vehicleId);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("customers", customerService.activeCustomers());
        model.addAttribute("activeMenu", "workorders");
        return "workorders/list";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Long customerId,
                                            @RequestParam(required = false) Long vehicleId,
                                            @RequestParam(required = false)
                                            @org.springframework.format.annotation.DateTimeFormat(iso =
                                                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                            LocalDate start,
                                            @RequestParam(required = false)
                                            @org.springframework.format.annotation.DateTimeFormat(iso =
                                                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                            LocalDate end) {
        return DownloadUtil.csv(exportService.workOrdersCsv(parseStatus(status), q, customerId, vehicleId,
                start, end), "is-emirleri.csv");
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) Long vehicleId,
                                              @RequestParam(required = false)
                                              @org.springframework.format.annotation.DateTimeFormat(iso =
                                                      org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                              LocalDate start,
                                              @RequestParam(required = false)
                                              @org.springframework.format.annotation.DateTimeFormat(iso =
                                                      org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                              LocalDate end) {
        return DownloadUtil.xlsx(exportService.workOrdersExcel(parseStatus(status), q, customerId, vehicleId,
                start, end), "is-emirleri.xlsx");
    }

    @GetMapping("/board")
    public String board(@RequestParam(required = false) String q,
                        @RequestParam(required = false) String age,
                        Model model) {
        String needle = SearchNormalizer.normalize(q);
        String ageFilter = normalizeBoardAge(age);
        List<WorkOrderBoardRow> openRows = workOrderService.boardOpenRows().stream()
                .filter(row -> matchesBoardSearch(row.order(), needle))
                .filter(row -> matchesBoardAge(row, ageFilter))
                .toList();
        List<WorkOrder> finishedToday = workOrderService.finishedToday().stream()
                .filter(order -> matchesBoardSearch(order, needle))
                .toList();
        model.addAttribute("openRows", openRows);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("age", ageFilter);
        model.addAttribute("waitingRows", openRows.stream()
                .filter(row -> row.order().getStatus() == WorkOrderStatus.BEKLIYOR).toList());
        model.addAttribute("inProgressRows", openRows.stream()
                .filter(row -> row.order().getStatus() == WorkOrderStatus.ISLEMDE).toList());
        model.addAttribute("completedRows", finishedToday.stream()
                .filter(order -> order.getStatus() == WorkOrderStatus.TAMAMLANDI).toList());
        model.addAttribute("deliveredRows", finishedToday.stream()
                .filter(order -> order.getStatus() == WorkOrderStatus.TESLIM_EDILDI).toList());
        model.addAttribute("finishedToday", finishedToday);
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("activeMenu", "board");
        return "workorders/board";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String createForm(@RequestParam(required = false) Long vehicleId,
                             @RequestParam(required = false) String plate,
                             @AuthenticationPrincipal AppUserPrincipal principal,
                             HttpSession session, Model model) {
        QuickWorkOrderForm quickForm = new QuickWorkOrderForm();
        if (plate != null && !plate.isBlank()) {
            quickForm.setPlate(plate.trim());
        } else if (vehicleId != null) {
            quickForm.setPlate(vehicleService.findById(vehicleId).getPlate());
        }
        quickForm.setAssignedUserId(principal == null ? null : principal.getId());
        return newOrderView(quickForm, session, model);
    }

    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String createNewOrder(@Valid @ModelAttribute("quickForm") QuickWorkOrderForm quickForm,
                                 BindingResult bindingResult,
                                 @RequestParam(name = "formToken", required = false) String formToken,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        try {
            formTokenService.consume(session, formToken);
        } catch (BusinessRuleException ex) {
            bindingResult.reject("form.token", ex.getMessage());
        }
        if (quickForm.getAssignedUserId() == null && principal != null) {
            quickForm.setAssignedUserId(principal.getId());
        }
        if (quickForm.getComplaint() == null || quickForm.getComplaint().isBlank()) {
            bindingResult.rejectValue("complaint", "complaint.required",
                    "Müşterinin ne dediğini yazın.");
        }
        if (quickForm.getServiceDescription() == null || quickForm.getServiceDescription().isBlank()) {
            bindingResult.rejectValue("serviceDescription", "service.required",
                    "Yapılacak işi yazın veya hazır seçeneklerden birini seçin.");
        }
        if (bindingResult.hasErrors()) {
            return newOrderView(quickForm, session, model);
        }
        try {
            quickForm.setCompleteNow(false);
            WorkOrder order = quickWorkOrderService.create(quickForm);
            redirectAttributes.addFlashAttribute("successMessage", "İş emri açıldı.");
            redirectAttributes.addFlashAttribute("quickCreated", true);
            redirectAttributes.addFlashAttribute("quickCompleted", false);
            return "redirect:/work-orders/" + order.getId();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("quick.failed", ex.getMessage());
            return newOrderView(quickForm, session, model);
        }
    }

    private String newOrderView(QuickWorkOrderForm quickForm, HttpSession session, Model model) {
        model.addAttribute("quickForm", quickForm);
        model.addAttribute("users", userService.findAll().stream().filter(User::isEnabled).toList());
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "workorders");
        return "workorders/new";
    }

    @PostMapping("/quick")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String quickCreate(@Valid @ModelAttribute("quickForm") QuickWorkOrderForm quickForm,
                              BindingResult bindingResult,
                              @RequestParam(name = "formToken", required = false) String formToken,
                              @RequestParam(name = "source", defaultValue = "inline") String source,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        try {
            formTokenService.consume(session, formToken);
        } catch (BusinessRuleException ex) {
            bindingResult.reject("form.token", ex.getMessage());
        }
        if (bindingResult.hasErrors()) {
            return quickFormView(quickForm, source, session, model);
        }
        try {
            WorkOrder order = quickWorkOrderService.create(quickForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    quickForm.isCompleteNow()
                            ? "İş emri oluşturuldu ve tamamlandı."
                            : "İş emri hızlı kayıtla oluşturuldu.");
            redirectAttributes.addFlashAttribute("quickCreated", true);
            redirectAttributes.addFlashAttribute("quickCompleted", quickForm.isCompleteNow());
            if (quickForm.isCompleteNow()) {
                redirectAttributes.addFlashAttribute("undoOrderId", order.getId());
                redirectAttributes.addFlashAttribute("undoReturnTo", "detail");
            }
            return "redirect:/work-orders/" + order.getId();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("quick.failed", ex.getMessage());
            return quickFormView(quickForm, source, session, model);
        }
    }

    @GetMapping("/quick")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String quickForm(@RequestParam(required = false) String plate,
                            HttpSession session, Model model) {
        QuickWorkOrderForm quickForm = new QuickWorkOrderForm();
        if (plate != null && !plate.isBlank()) {
            quickForm.setPlate(plate.trim());
        }
        model.addAttribute("quickForm", quickForm);
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "workorders");
        return "workorders/quick";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String create(@Valid @ModelAttribute("workOrderForm") WorkOrderForm form,
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
            addFormOptions(model, false);
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "workorders");
            return "workorders/form";
        }
        try {
            WorkOrder order = workOrderService.create(form);
            redirectAttributes.addFlashAttribute("successMessage", "İş emri oluşturuldu.");
            return "redirect:/work-orders/" + order.getId();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("workorder.failed", ex.getMessage());
            addFormOptions(model, false);
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "workorders");
            return "workorders/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        WorkOrder order = workOrderService.findById(id);
        model.addAttribute("order", order);
        model.addAttribute("itemForm", new WorkOrderItemForm());
        populateDetailModel(order, principal, model);
        return "workorders/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String editForm(@PathVariable Long id, Model model) {
        WorkOrder order = workOrderService.findById(id);
        model.addAttribute("order", order);
        model.addAttribute("workOrderForm", workOrderService.toForm(order));
        addFormOptions(model, true);
        model.addAttribute("activeMenu", "workorders");
        return "workorders/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("workOrderForm") WorkOrderForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        WorkOrder order = workOrderService.findById(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("order", order);
            addFormOptions(model, true);
            model.addAttribute("activeMenu", "workorders");
            return "workorders/form";
        }
        try {
            workOrderService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "İş emri güncellendi.");
            return "redirect:/work-orders/" + id;
        } catch (BusinessRuleException ex) {
            bindingResult.reject("workorder.failed", ex.getMessage());
            model.addAttribute("order", order);
            addFormOptions(model, true);
            model.addAttribute("activeMenu", "workorders");
            return "workorders/form";
        }
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String paymentMethod,
                               @RequestParam(defaultValue = "detail") String returnTo,
                               RedirectAttributes redirectAttributes) {
        try {
            WorkOrderStatus target = parseStatus(status);
            if (target == null) {
                throw new BusinessRuleException("Geçerli bir durum seçilmelidir.");
            }
            PaymentMethod method = parsePaymentMethod(paymentMethod);
            if (target == WorkOrderStatus.TAMAMLANDI && method != null) {
                Payment payment = workOrderService.completeWithPayment(id, method);
                if (payment == null) {
                    redirectAttributes.addFlashAttribute("successMessage", "İş emri tamamlandı.");
                    redirectAttributes.addFlashAttribute("undoOrderId", id);
                    redirectAttributes.addFlashAttribute("undoReturnTo", returnTo);
                } else {
                    redirectAttributes.addFlashAttribute("successMessage",
                            "İş emri tamamlandı ve " + method.getLabel() + " tahsilatı alındı.");
                }
            } else {
                workOrderService.changeStatus(id, target);
                redirectAttributes.addFlashAttribute("successMessage", "İş emri durumu güncellendi.");
                if (target == WorkOrderStatus.TAMAMLANDI) {
                    redirectAttributes.addFlashAttribute("undoOrderId", id);
                    redirectAttributes.addFlashAttribute("undoReturnTo", returnTo);
                }
            }
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return statusRedirect(id, returnTo);
    }

    @PostMapping("/{id}/undo-complete")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String undoComplete(@PathVariable Long id,
                               @RequestParam(defaultValue = "detail") String returnTo,
                               RedirectAttributes redirectAttributes) {
        try {
            workOrderService.undoCompletion(id);
            redirectAttributes.addFlashAttribute("successMessage", "Tamamlama geri alındı.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return statusRedirect(id, returnTo);
    }

    private PaymentMethod parsePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Geçersiz ödeme yöntemi.");
        }
    }

    private String normalizeBoardAge(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "today", "yesterday", "old" -> normalized;
            default -> "";
        };
    }

    private boolean matchesBoardAge(WorkOrderBoardRow row, String filter) {
        return switch (filter) {
            case "today" -> row.daysOpen() == 0;
            case "yesterday" -> row.daysOpen() == 1;
            case "old" -> row.daysOpen() >= 2;
            default -> true;
        };
    }

    private boolean matchesBoardSearch(WorkOrder order, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        return SearchNormalizer.normalize(order.getOrderNo()).contains(needle)
                || SearchNormalizer.normalize(order.getVehicle().getPlate()).contains(needle)
                || SearchNormalizer.normalize(order.getCustomer().getName()).contains(needle)
                || SearchNormalizer.normalize(order.getCustomer().getPhone()).contains(needle);
    }

    private String statusRedirect(Long id, String returnTo) {
        if ("board".equals(returnTo)) {
            return "redirect:/work-orders/board";
        }
        if ("dashboard".equals(returnTo)) {
            return "redirect:/";
        }
        return "redirect:/work-orders/" + id;
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String addItem(@PathVariable Long id,
                          @Valid @ModelAttribute("itemForm") WorkOrderItemForm form,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal AppUserPrincipal principal,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            WorkOrder order = workOrderService.findById(id);
            model.addAttribute("order", order);
            populateDetailModel(order, principal, model);
            return "workorders/detail";
        }
        try {
            workOrderService.addItem(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Kalem eklendi.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/work-orders/" + id;
    }

    @GetMapping("/{id}/items/{itemId}/edit")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String editItemForm(@PathVariable Long id, @PathVariable Long itemId, Model model) {
        WorkOrder order = workOrderService.findById(id);
        WorkOrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> tr.com.servisdefteri.common.NotFoundException.of("İş emri kalemi", itemId));
        model.addAttribute("order", order);
        model.addAttribute("item", item);
        model.addAttribute("itemForm", workOrderService.toItemForm(item));
        model.addAttribute("itemTypes", WorkOrderItemType.values());
        model.addAttribute("activeMenu", "workorders");
        return "workorders/item-form";
    }

    @PostMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String updateItem(@PathVariable Long id, @PathVariable Long itemId,
                             @Valid @ModelAttribute("itemForm") WorkOrderItemForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            WorkOrder order = workOrderService.findById(id);
            WorkOrderItem item = order.getItems().stream()
                    .filter(i -> i.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> tr.com.servisdefteri.common.NotFoundException.of("İş emri kalemi", itemId));
            model.addAttribute("order", order);
            model.addAttribute("item", item);
            model.addAttribute("itemTypes", WorkOrderItemType.values());
            model.addAttribute("activeMenu", "workorders");
            return "workorders/item-form";
        }
        try {
            workOrderService.updateItem(id, itemId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Kalem güncellendi.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/work-orders/" + id;
    }

    @PostMapping("/{id}/items/{itemId}/delete")
    @PreAuthorize("hasAnyRole('PATRON','SERVIS')")
    public String deleteItem(@PathVariable Long id, @PathVariable Long itemId,
                             RedirectAttributes redirectAttributes) {
        try {
            workOrderService.removeItem(id, itemId);
            redirectAttributes.addFlashAttribute("successMessage", "Kalem kaldırıldı.");
        } catch (BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/work-orders/" + id;
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        WorkOrder order = workOrderService.findById(id);
        List<WorkOrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing(WorkOrderItem::getId))
                .toList();
        model.addAttribute("order", order);
        model.addAttribute("items", items);
        model.addAttribute("business", businessSettingsService.get());
        return "workorders/print";
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        WorkOrder order = workOrderService.findById(id);
        return DownloadUtil.pdf(pdfService.workOrderPdf(order),
                "is-emri-" + order.getOrderNo() + ".pdf");
    }

    private void populateDetailModel(WorkOrder order, AppUserPrincipal principal, Model model) {
        List<WorkOrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing(WorkOrderItem::getId))
                .toList();
        boolean canCancel = principal.getRole() == Role.PATRON || principal.getRole() == Role.MUHASEBE;
        boolean canOperate = principal.getRole() == Role.PATRON || principal.getRole() == Role.SERVIS;
        model.addAttribute("items", items);
        model.addAttribute("allowedTransitions", order.getStatus().allowedTransitions().stream()
                .filter(status -> status == WorkOrderStatus.IPTAL ? canCancel : canOperate)
                .toList());
        model.addAttribute("canCancel", canCancel);
        model.addAttribute("canOperate", canOperate);
        if (canCancel) {
            model.addAttribute("customerBalance", accountService.balance(order.getCustomer().getId()));
        }
        String shareText = "Toyota Plaza Öztoprak - " + order.getOrderNo() + " iş emri. "
                + "Araç: " + order.getVehicle().getPlate() + ". "
                + "Durum: " + order.getStatus().getLabel() + ". "
                + "Toplam: " + CsvUtil.money(order.getTotalAmount()) + " TL.";
        model.addAttribute("whatsappUrl", "https://wa.me/?text="
                + URLEncoder.encode(shareText, StandardCharsets.UTF_8));
        model.addAttribute("itemTypes", WorkOrderItemType.values());
        model.addAttribute("itemTemplates",
                itemTemplateRepository.findByActiveTrueOrderBySortOrderAscNameAsc());
        model.addAttribute("intake", documentService.findIntake(order.getId()));
        model.addAttribute("delivery", documentService.findDelivery(order.getId()));
        model.addAttribute("activeMenu", "workorders");
    }

    private void addFormOptions(Model model, boolean includeInactive) {
        List<User> users = userService.findAll().stream().filter(User::isEnabled).toList();
        model.addAttribute("customers", includeInactive ? customerService.allCustomers()
                : customerService.activeCustomers());
        model.addAttribute("vehicles", includeInactive ? vehicleService.allVehicles()
                : vehicleService.activeVehicles());
        model.addAttribute("users", users);
    }

    private String quickFormView(QuickWorkOrderForm quickForm, String source,
                                 HttpSession session, Model model) {
        if ("quick".equals(source)) {
            model.addAttribute("quickForm", quickForm);
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "workorders");
            return "workorders/quick";
        }
        WorkOrderForm orderForm = new WorkOrderForm();
        orderForm.setOpenedAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
        model.addAttribute("workOrderForm", orderForm);
        addFormOptions(model, false);
        model.addAttribute("plateNotFound", quickForm.getPlate());
        model.addAttribute("plateQuery", quickForm.getPlate());
        model.addAttribute("quickForm", quickForm);
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "workorders");
        return "workorders/form";
    }

    private WorkOrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WorkOrderStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return null;
        }
    }
}
