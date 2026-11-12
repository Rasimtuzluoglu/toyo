package tr.com.servisdefteri.report;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tr.com.servisdefteri.account.CustomerBalance;
import tr.com.servisdefteri.account.LedgerStatement;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.common.SearchNormalizer;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.report.AgingRow;
import tr.com.servisdefteri.payment.CollectionSummary;
import tr.com.servisdefteri.settings.BusinessSettingsService;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderService;
import tr.com.servisdefteri.workorder.WorkOrderStatus;
import tr.com.servisdefteri.workorder.WorkOrderStatusSummary;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final WorkOrderService workOrderService;
    private final BusinessSettingsService businessSettingsService;
    private final Clock clock;

    public ReportController(ReportService reportService, CustomerService customerService,
                            VehicleService vehicleService, WorkOrderService workOrderService,
                            BusinessSettingsService businessSettingsService, Clock clock) {
        this.reportService = reportService;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.workOrderService = workOrderService;
        this.businessSettingsService = businessSettingsService;
        this.clock = clock;
    }

    @GetMapping
    public String index(Model model) {
        LocalDate start = LocalDate.now(clock).withDayOfMonth(1);
        LocalDate end = LocalDate.now(clock);
        model.addAttribute("monthCollections", reportService.collectionsTotal(start, end));
        model.addAttribute("openWorkOrders", workOrderService.openCount());
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("activeMenu", "reports");
        return "reports/index";
    }

    @GetMapping("/collections")
    public String collections(@RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                              Model model) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? endDate.withDayOfMonth(1) : start;
        List<CollectionSummary> rows = reportService.collections(startDate, endDate);
        model.addAttribute("rows", rows);
        model.addAttribute("total", reportService.collectionsTotal(startDate, endDate));
        model.addAttribute("start", startDate);
        model.addAttribute("end", endDate);
        model.addAttribute("activeMenu", "reports");
        return "reports/collections";
    }

    @GetMapping("/collections.csv")
    public ResponseEntity<byte[]> collectionsCsv(@RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? endDate.withDayOfMonth(1) : start;
        return csv(reportService.collectionsCsv(startDate, endDate),
                "tahsilat-raporu-" + startDate + "-" + endDate + ".csv");
    }

    @GetMapping("/collections.xlsx")
    public ResponseEntity<byte[]> collectionsExcel(@RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? endDate.withDayOfMonth(1) : start;
        return DownloadUtil.xlsx(reportService.collectionsExcel(startDate, endDate),
                "tahsilat-raporu-" + startDate + "-" + endDate + ".xlsx");
    }

    @GetMapping("/balances")
    public String balances(@RequestParam(required = false) String q, Model model) {
        List<CustomerBalance> rows = reportService.balances();
        if (q != null && !q.isBlank()) {
            String needle = SearchNormalizer.normalize(q);
            rows = rows.stream()
                    .filter(b -> SearchNormalizer.normalize(b.name()).contains(needle))
                    .toList();
        }
        BigDecimal receivables = rows.stream().filter(CustomerBalance::isDebtor)
                .map(CustomerBalance::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = rows.stream().filter(b -> b.balance().signum() < 0)
                .map(CustomerBalance::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("rows", rows);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("receivables", receivables);
        model.addAttribute("credits", credits);
        model.addAttribute("activeMenu", "reports");
        return "reports/balances";
    }

    @GetMapping("/balances.csv")
    public ResponseEntity<byte[]> balancesCsv() {
        return csv(reportService.balancesCsv(), "musteri-bakiyeleri-" + LocalDate.now(clock) + ".csv");
    }

    @GetMapping("/balances.xlsx")
    public ResponseEntity<byte[]> balancesExcel() {
        return DownloadUtil.xlsx(reportService.balancesExcel(),
                "musteri-bakiyeleri-" + LocalDate.now(clock) + ".xlsx");
    }

    @GetMapping("/aging")
    public String aging(Model model) {
        List<AgingRow> rows = reportService.aging(LocalDate.now(clock));
        BigDecimal current = rows.stream().map(AgingRow::current).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mid = rows.stream().map(AgingRow::mid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal old = rows.stream().map(AgingRow::old).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("rows", rows);
        model.addAttribute("currentTotal", current);
        model.addAttribute("midTotal", mid);
        model.addAttribute("oldTotal", old);
        model.addAttribute("grandTotal", current.add(mid).add(old));
        model.addAttribute("activeMenu", "reports");
        return "reports/aging";
    }

    @GetMapping("/aging.csv")
    public ResponseEntity<byte[]> agingCsv() {
        return DownloadUtil.csv(reportService.agingCsv(LocalDate.now(clock)),
                "cari-yaslandirma-" + LocalDate.now(clock) + ".csv");
    }

    @GetMapping("/aging.xlsx")
    public ResponseEntity<byte[]> agingExcel() {
        return DownloadUtil.xlsx(reportService.agingExcel(LocalDate.now(clock)),
                "cari-yaslandirma-" + LocalDate.now(clock) + ".xlsx");
    }

    @GetMapping("/maintenance")
    public String maintenance(Model model) {
        model.addAttribute("rows", reportService.maintenanceReminders());
        model.addAttribute("activeMenu", "reports");
        return "reports/maintenance";
    }

    @GetMapping("/maintenance.csv")
    public ResponseEntity<byte[]> maintenanceCsv() {
        return DownloadUtil.csv(reportService.maintenanceCsv(),
                "bakim-hatirlatmalari-" + LocalDate.now(clock) + ".csv");
    }

    @GetMapping("/maintenance.xlsx")
    public ResponseEntity<byte[]> maintenanceExcel() {
        return DownloadUtil.xlsx(reportService.maintenanceExcel(),
                "bakim-hatirlatmalari-" + LocalDate.now(clock) + ".xlsx");
    }

    @GetMapping("/work-orders")
    public String workOrders(@RequestParam(required = false) String status,
                             @RequestParam(required = false) Long vehicleId,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                             @RequestParam(defaultValue = "0") int page,
                             Model model) {
        WorkOrderStatus statusFilter = parseStatus(status);
        Page<WorkOrder> orders = reportService.workOrders(statusFilter, vehicleId, start, end,
                Math.max(page, 0));
        List<WorkOrderStatusSummary> summary = reportService.workOrderStatuses(start, end);
        model.addAttribute("orders", orders);
        model.addAttribute("summary", summary);
        model.addAttribute("statuses", WorkOrderStatus.values());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("vehicleId", vehicleId);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("vehicles", vehicleService.allVehicles());
        model.addAttribute("activeMenu", "reports");
        return "reports/work-orders";
    }

    @GetMapping("/work-orders.csv")
    public ResponseEntity<byte[]> workOrdersCsv(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long vehicleId,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return csv(reportService.workOrdersCsv(parseStatus(status), vehicleId, start, end),
                "is-emirleri-" + LocalDate.now(clock) + ".csv");
    }

    @GetMapping("/work-orders.xlsx")
    public ResponseEntity<byte[]> workOrdersExcel(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long vehicleId,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return DownloadUtil.xlsx(reportService.workOrdersExcel(parseStatus(status), vehicleId, start, end),
                "is-emirleri-" + LocalDate.now(clock) + ".xlsx");
    }

    @GetMapping("/service-parts")
    public String serviceParts(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                               Model model) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? endDate.withDayOfMonth(1) : start;
        List<ServicePartTotal> rows = reportService.servicePartTotals(startDate, endDate);
        BigDecimal total = rows.stream().map(ServicePartTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("rows", rows);
        model.addAttribute("total", total);
        model.addAttribute("workOrderTotal", reportService.totalWorkOrderAmount(startDate, endDate));
        model.addAttribute("start", startDate);
        model.addAttribute("end", endDate);
        model.addAttribute("activeMenu", "reports");
        return "reports/service-parts";
    }

    @GetMapping("/service-parts.csv")
    public ResponseEntity<byte[]> servicePartsCsv(@RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? endDate.withDayOfMonth(1) : start;
        return DownloadUtil.csv(reportService.servicePartsCsv(startDate, endDate),
                "iscilik-parca-" + startDate + "-" + endDate + ".csv");
    }

    @GetMapping("/service-parts.xlsx")
    public ResponseEntity<byte[]> servicePartsExcel(@RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? endDate.withDayOfMonth(1) : start;
        return DownloadUtil.xlsx(reportService.servicePartsExcel(startDate, endDate),
                "iscilik-parca-" + startDate + "-" + endDate + ".xlsx");
    }

    @GetMapping("/vehicle-history")
    public String vehicleHistory(@RequestParam(required = false) Long vehicleId,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                 @RequestParam(defaultValue = "0") int page,
                                 Model model) {
        List<Vehicle> vehicles = vehicleService.allVehicles();
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("vehicleId", vehicleId);
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        if (vehicleId != null) {
            Vehicle vehicle = vehicleService.findById(vehicleId);
            model.addAttribute("selectedVehicle", vehicle);
            model.addAttribute("orders", reportService.workOrders(null, vehicleId, start, end,
                    Math.max(page, 0)));
            model.addAttribute("lastServiceDate", workOrderService.lastServiceDate(vehicleId));
        }
        model.addAttribute("activeMenu", "reports");
        return "reports/vehicle-history";
    }

    @GetMapping("/vehicle-history.csv")
    public ResponseEntity<byte[]> vehicleHistoryCsv(@RequestParam(required = false) Long vehicleId,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return DownloadUtil.csv(reportService.workOrdersCsv(null, vehicleId, start, end),
                "arac-servis-gecmisi-" + LocalDate.now(clock) + ".csv");
    }

    @GetMapping("/vehicle-history.xlsx")
    public ResponseEntity<byte[]> vehicleHistoryExcel(@RequestParam(required = false) Long vehicleId,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return DownloadUtil.xlsx(reportService.workOrdersExcel(null, vehicleId, start, end),
                "arac-servis-gecmisi-" + LocalDate.now(clock) + ".xlsx");
    }

    @GetMapping("/ledger")
    public String ledger(@RequestParam(required = false) Long customerId,
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                         Model model) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? LocalDate.now(clock).minusMonths(12) : start;
        model.addAttribute("customers", customerService.allCustomers());
        model.addAttribute("customerId", customerId);
        model.addAttribute("start", startDate);
        model.addAttribute("end", endDate);
        if (customerId != null) {
            model.addAttribute("statement", reportService.ledger(customerId, startDate, endDate));
        }
        model.addAttribute("business", businessSettingsService.get());
        model.addAttribute("activeMenu", "reports");
        return "reports/ledger";
    }

    @GetMapping("/ledger.csv")
    public ResponseEntity<byte[]> ledgerCsv(@RequestParam Long customerId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? LocalDate.now(clock).minusMonths(12) : start;
        LedgerStatement statement = reportService.ledger(customerId, startDate, endDate);
        return csv(reportService.ledgerCsv(statement),
                "cari-hareketler-" + customerId + "-" + startDate + "-" + endDate + ".csv");
    }

    @GetMapping("/ledger.xlsx")
    public ResponseEntity<byte[]> ledgerExcel(@RequestParam Long customerId,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? LocalDate.now(clock).minusMonths(12) : start;
        LedgerStatement statement = reportService.ledger(customerId, startDate, endDate);
        return DownloadUtil.xlsx(reportService.ledgerExcel(statement),
                "cari-hareketler-" + customerId + "-" + startDate + "-" + endDate + ".xlsx");
    }

    private ResponseEntity<byte[]> csv(byte[] content, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }

    private WorkOrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WorkOrderStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
