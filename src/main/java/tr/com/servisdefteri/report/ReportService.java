package tr.com.servisdefteri.report;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.CustomerBalance;
import tr.com.servisdefteri.account.LedgerRow;
import tr.com.servisdefteri.account.LedgerStatement;
import tr.com.servisdefteri.common.CsvUtil;
import tr.com.servisdefteri.common.ExcelUtil;
import tr.com.servisdefteri.payment.CollectionSummary;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderItemRepository;
import tr.com.servisdefteri.workorder.WorkOrderItemType;
import tr.com.servisdefteri.workorder.WorkOrderRepository;
import tr.com.servisdefteri.workorder.WorkOrderService;
import tr.com.servisdefteri.workorder.WorkOrderStatus;
import tr.com.servisdefteri.workorder.WorkOrderStatusSummary;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ReportService {

    private final PaymentService paymentService;
    private final AccountService accountService;
    private final WorkOrderService workOrderService;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderItemRepository itemRepository;
    private final VehicleService vehicleService;
    private final Clock clock;

    public ReportService(PaymentService paymentService, AccountService accountService,
                         WorkOrderService workOrderService, WorkOrderRepository workOrderRepository,
                         WorkOrderItemRepository itemRepository, VehicleService vehicleService,
                         Clock clock) {
        this.paymentService = paymentService;
        this.accountService = accountService;
        this.workOrderService = workOrderService;
        this.workOrderRepository = workOrderRepository;
        this.itemRepository = itemRepository;
        this.vehicleService = vehicleService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CollectionSummary> collections(LocalDate start, LocalDate end) {
        return paymentService.summaryBetween(start, end);
    }

    @Transactional(readOnly = true)
    public BigDecimal collectionsTotal(LocalDate start, LocalDate end) {
        return paymentService.totalBetween(start, end);
    }

    @Transactional(readOnly = true)
    public List<CustomerBalance> balances() {
        return accountService.customerBalances();
    }

    @Transactional(readOnly = true)
    public LedgerStatement ledger(Long customerId, LocalDate start, LocalDate end) {
        return accountService.statement(customerId, start, end);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceReminder> maintenanceReminders() {
        LocalDate today = LocalDate.now(clock);
        List<MaintenanceReminder> result = new ArrayList<>();
        java.util.Map<Long, WorkOrder> latest = workOrderService.latestFinishedByVehicle();
        for (Vehicle vehicle : vehicleService.allVehicles()) {
            if (!vehicle.isActive()) {
                continue;
            }
            WorkOrder last = latest.get(vehicle.getId());
            if (last == null) {
                continue;
            }
            LocalDateTime serviceAt = last.getDeliveredAt() != null ? last.getDeliveredAt()
                    : (last.getClosedAt() != null ? last.getClosedAt() : last.getOpenedAt());
            if (serviceAt == null) {
                continue;
            }
            int days = (int) ChronoUnit.DAYS.between(serviceAt.toLocalDate(), today);
            int lastMileage = last.getMileage() != null ? last.getMileage() : vehicle.getCurrentMileage();
            int kmSince = Math.max(0, vehicle.getCurrentMileage() - lastMileage);
            boolean timeDue = days >= 180;
            boolean kmDue = kmSince >= 10_000;
            if (!timeDue && !kmDue) {
                continue;
            }
            String reason = timeDue && kmDue ? "Süre ve kilometre"
                    : timeDue ? "180 günden uzun süre" : "10.000 km üzeri";
            result.add(new MaintenanceReminder(
                    vehicle.getCustomer().getId(), vehicle.getCustomer().getName(),
                    vehicle.getCustomer().getPhone(), vehicle.getId(), vehicle.getPlate(),
                    vehicle.getDisplayName(), serviceAt.toLocalDate(), days, kmSince, reason));
        }
        result.sort(Comparator.comparingInt(MaintenanceReminder::daysSince).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public byte[] maintenanceCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Müşteri", "Telefon", "Plaka", "Araç", "Son Servis",
                "Geçen Gün", "Kilometre Farkı", "Neden"));
        for (MaintenanceReminder row : maintenanceReminders()) {
            sb.append(CsvUtil.row(row.customerName(), row.phone(), row.plate(), row.vehicleName(),
                    row.lastServiceDate(), row.daysSince(), row.kmSince(), row.reason()));
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] maintenanceExcel() {
        List<List<Object>> rows = new ArrayList<>();
        for (MaintenanceReminder row : maintenanceReminders()) {
            rows.add(row(row.customerName(), row.phone(), row.plate(), row.vehicleName(),
                    row.lastServiceDate(), row.daysSince(), row.kmSince(), row.reason()));
        }
        return ExcelUtil.toXlsx("Bakım Hatırlatmaları", "Bakım Zamanı Gelen Araçlar",
                List.of("Rapor tarihi: " + LocalDate.now(clock)),
                List.of("Müşteri", "Telefon", "Plaka", "Araç", "Son Servis",
                        "Geçen Gün", "Kilometre Farkı", "Neden"), rows);
    }

    @Transactional(readOnly = true)
    public List<AgingRow> aging(LocalDate today) {
        List<AgingRow> result = new ArrayList<>();
        for (CustomerBalance balance : balances()) {
            if (!balance.isDebtor()) {
                continue;
            }
            BigDecimal current = BigDecimal.ZERO;
            BigDecimal mid = BigDecimal.ZERO;
            BigDecimal old = BigDecimal.ZERO;
            for (var debt : accountService.openDebts(balance.customerId())) {
                long age = java.time.temporal.ChronoUnit.DAYS.between(debt.date(), today);
                if (age <= 30) {
                    current = current.add(debt.getRemaining());
                } else if (age <= 60) {
                    mid = mid.add(debt.getRemaining());
                } else {
                    old = old.add(debt.getRemaining());
                }
            }
            result.add(new AgingRow(balance.customerId(), balance.name(), balance.phone(),
                    current, mid, old));
        }
        result.sort(java.util.Comparator.comparing(AgingRow::total).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public byte[] agingCsv(LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Müşteri", "Telefon", "0-30 Gün", "31-60 Gün", "61+ Gün", "Toplam"));
        for (AgingRow row : aging(today)) {
            sb.append(CsvUtil.row(row.name(), row.phone(), csvMoney(row.current()),
                    csvMoney(row.mid()), csvMoney(row.old()), csvMoney(row.total())));
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] agingExcel(LocalDate today) {        List<List<Object>> rows = new ArrayList<>();
        for (AgingRow row : aging(today)) {
            rows.add(row(row.name(), row.phone(), row.current(), row.mid(), row.old(), row.total()));
        }
        return ExcelUtil.toXlsx("Yaşlandırma", "Cari Yaşlandırma Raporu",
                List.of("Rapor tarihi: " + today),
                List.of("Müşteri", "Telefon", "0-30 Gün", "31-60 Gün", "61+ Gün", "Toplam"), rows);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderStatusSummary> workOrderStatuses(LocalDate start, LocalDate end) {
        return workOrderRepository.statusSummary(start == null ? null : start.atStartOfDay(),
                end == null ? null : end.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public Page<WorkOrder> workOrders(WorkOrderStatus status, Long vehicleId,
                                      LocalDate start, LocalDate end, int page) {
        return workOrderService.search(status, null, vehicleId, start, end, null, page, 25);
    }

    @Transactional(readOnly = true)
    public List<ServicePartTotal> servicePartTotals(LocalDate start, LocalDate end) {        List<ServicePartTotal> result = new ArrayList<>();
        for (Object[] row : itemRepository.totalsByType(WorkOrderStatus.IPTAL,
                start == null ? null : start.atStartOfDay(),
                end == null ? null : end.plusDays(1).atStartOfDay())) {
            result.add(new ServicePartTotal((WorkOrderItemType) row[0], (BigDecimal) row[1]));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BigDecimal totalWorkOrderAmount(LocalDate start, LocalDate end) {
        BigDecimal total = workOrderRepository.sumTotal(WorkOrderStatus.IPTAL,
                start == null ? null : start.atStartOfDay(),
                end == null ? null : end.plusDays(1).atStartOfDay());
        return total == null ? BigDecimal.ZERO : total;
    }

    @Transactional(readOnly = true)
    public byte[] collectionsCsv(LocalDate start, LocalDate end) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Tarih", "Ödeme Yöntemi", "İşlem Adedi", "Tutar"));
        long totalCount = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (CollectionSummary row : collections(start, end)) {
            sb.append(CsvUtil.row(row.date(), methodLabel(row.method()), row.count(), csvMoney(row.total())));
            totalCount += row.count();
            total = total.add(row.total());
        }
        sb.append('\n');
        sb.append(CsvUtil.row("Toplam", "", totalCount, csvMoney(total)));
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] balancesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Müşteri", "Telefon", "Bakiye", "Durum"));
        for (CustomerBalance row : balances()) {
            sb.append(CsvUtil.row(row.name(), row.phone(), csvMoney(row.balance()),
                    row.isDebtor() ? "Borçlu" : (row.balance().signum() < 0 ? "Alacaklı" : "Dengede")));
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    public byte[] ledgerCsv(LedgerStatement statement) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Müşteri", statement.customer().getName()));
        sb.append(CsvUtil.row("Dönem", statement.start() + " - " + statement.end()));
        sb.append(CsvUtil.row("Açılış Bakiyesi", csvMoney(statement.openingBalance())));
        sb.append('\n');
        sb.append(CsvUtil.row("Tarih", "Tür", "Açıklama", "İş Emri", "Borç", "Alacak", "Bakiye"));
        for (LedgerRow row : statement.rows()) {
            sb.append(CsvUtil.row(row.date(), row.type().getLabel(), row.description(),
                    row.workOrderNo(), csvMoney(row.debit()), csvMoney(row.credit()), csvMoney(row.balance())));
        }
        sb.append('\n');
        sb.append(CsvUtil.row("Toplam Borç", csvMoney(statement.totalDebit())));
        sb.append(CsvUtil.row("Toplam Alacak", csvMoney(statement.totalCredit())));
        sb.append(CsvUtil.row("Kapanış Bakiyesi", csvMoney(statement.closingBalance())));
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] workOrdersCsv(WorkOrderStatus status, Long vehicleId, LocalDate start, LocalDate end) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("İş Emri", "Açılış", "Müşteri", "Araç", "Durum",
                "İşçilik", "Parça", "Toplam"));
        int pageNumber = 0;
        while (true) {
            Page<WorkOrder> page = workOrders(status, vehicleId, start, end, pageNumber);
            for (WorkOrder order : page.getContent()) {
                sb.append(CsvUtil.row(order.getOrderNo(),
                        order.getOpenedAt() == null ? "" : order.getOpenedAt().toLocalDate(),
                        order.getCustomer().getName(), order.getVehicle().getPlate(),
                        order.getStatus().getLabel(), csvMoney(order.getLaborTotal()),
                        csvMoney(order.getPartsTotal()), csvMoney(order.getTotalAmount())));
            }
            if (page.isLast()) {
                break;
            }
            pageNumber++;
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] collectionsExcel(LocalDate start, LocalDate end) {
        List<List<Object>> rows = new ArrayList<>();
        long totalCount = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (CollectionSummary row : collections(start, end)) {
            rows.add(row(row.date(), methodLabel(row.method()), row.count(), row.total()));
            totalCount += row.count();
            total = total.add(row.total());
        }
        rows.add(row("Toplam", "", totalCount, total));
        return ExcelUtil.toXlsx("Tahsilat", "Tahsilat Raporu",
                List.of("Dönem: " + start + " - " + end),
                List.of("Tarih", "Ödeme Yöntemi", "İşlem Adedi", "Tutar"), rows);
    }

    @Transactional(readOnly = true)
    public byte[] balancesExcel() {
        List<List<Object>> rows = new ArrayList<>();
        for (CustomerBalance row : balances()) {
            rows.add(row(row.name(), row.phone(), row.balance(),
                    row.isDebtor() ? "Borçlu" : (row.balance().signum() < 0 ? "Alacaklı" : "Dengede")));
        }
        return ExcelUtil.toXlsx("Bakiyeler", "Müşteri Bakiyeleri", null,
                List.of("Müşteri", "Telefon", "Bakiye", "Durum"), rows);
    }

    public byte[] ledgerExcel(LedgerStatement statement) {
        List<List<Object>> rows = new ArrayList<>();
        for (LedgerRow row : statement.rows()) {
            rows.add(row(row.date(), row.type().getLabel(), row.description(),
                    row.workOrderNo(), row.debit(), row.credit(), row.balance()));
        }
        rows.add(row("Toplam", "", "", "", statement.totalDebit(), statement.totalCredit(),
                statement.closingBalance()));
        return ExcelUtil.toXlsx("Cari Hareketler", "Cari Hareket Dökümü",
                List.of("Müşteri: " + statement.customer().getName(),
                        "Dönem: " + statement.start() + " - " + statement.end(),
                        "Açılış Bakiyesi: " + csvMoney(statement.openingBalance())),
                List.of("Tarih", "Tür", "Açıklama", "İş Emri", "Borç", "Alacak", "Bakiye"), rows);
    }

    @Transactional(readOnly = true)
    public byte[] workOrdersExcel(WorkOrderStatus status, Long vehicleId, LocalDate start, LocalDate end) {
        List<List<Object>> rows = new ArrayList<>();
        int pageNumber = 0;
        while (true) {
            Page<WorkOrder> page = workOrders(status, vehicleId, start, end, pageNumber);
            for (WorkOrder order : page.getContent()) {
                rows.add(row(order.getOrderNo(),
                        order.getOpenedAt() == null ? "" : order.getOpenedAt().toLocalDate(),
                        order.getCustomer().getName(), order.getVehicle().getPlate(),
                        order.getStatus().getLabel(), order.getLaborTotal(),
                        order.getPartsTotal(), order.getTotalAmount()));
            }
            if (page.isLast()) {
                break;
            }
            pageNumber++;
        }
        return ExcelUtil.toXlsx("İş Emirleri", "İş Emirleri",
                List.of("Rapor tarihi: " + LocalDate.now()),
                List.of("İş Emri", "Açılış", "Müşteri", "Araç", "Durum",
                        "İşçilik", "Parça", "Toplam"), rows);
    }

    @Transactional(readOnly = true)
    public byte[] servicePartsCsv(LocalDate start, LocalDate end) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Kalem Türü", "Tutar"));
        BigDecimal total = BigDecimal.ZERO;
        for (ServicePartTotal row : servicePartTotals(start, end)) {
            sb.append(CsvUtil.row(row.type().getLabel(), csvMoney(row.total())));
            total = total.add(row.total());
        }
        sb.append('\n');
        sb.append(CsvUtil.row("Toplam", csvMoney(total)));
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] servicePartsExcel(LocalDate start, LocalDate end) {
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ServicePartTotal row : servicePartTotals(start, end)) {
            rows.add(row(row.type().getLabel(), row.total()));
            total = total.add(row.total());
        }
        rows.add(row("Toplam", total));
        return ExcelUtil.toXlsx("İşçilik ve Parça", "İşçilik ve Parça Toplamları",
                List.of("Dönem: " + start + " - " + end),
                List.of("Kalem Türü", "Tutar"), rows);
    }

    public String csvMoney(BigDecimal value) {
        return CsvUtil.money(value);
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private String methodLabel(PaymentMethod method) {
        return method == null ? "" : method.getLabel();
    }
}
