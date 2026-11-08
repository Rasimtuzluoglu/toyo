package tr.com.servisdefteri.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.WorkOrderDebt;
import tr.com.servisdefteri.common.CsvUtil;
import tr.com.servisdefteri.common.ExcelUtil;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderItem;
import tr.com.servisdefteri.workorder.WorkOrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Seçilen günde açılan iş emirlerini tek dökümde toplar.
 */
@Service
public class DailyReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkOrderRepository workOrderRepository;
    private final AccountService accountService;

    public DailyReportService(WorkOrderRepository workOrderRepository, AccountService accountService) {
        this.workOrderRepository = workOrderRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public DailyReportView load(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<WorkOrder> orders = workOrderRepository.findOpenedBetween(start, end);
        List<DailyReportRow> rows = new ArrayList<>(orders.size());
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;

        for (WorkOrder order : orders) {
            WorkOrderDebt debt = accountService.workOrderDebt(order.getId());
            BigDecimal total = scale(order.getTotalAmount());
            BigDecimal paid = debt == null ? BigDecimal.ZERO : scale(debt.paid());
            BigDecimal remaining = scale(total.subtract(paid));
            if (remaining.signum() < 0) {
                remaining = BigDecimal.ZERO;
            }
            rows.add(new DailyReportRow(
                    order.getId(),
                    order.getOrderNo(),
                    order.getOpenedAt(),
                    order.getCustomer().getName(),
                    order.getCustomer().getPhone(),
                    order.getVehicle().getPlate(),
                    vehicleName(order),
                    safe(order.getComplaint()),
                    workDescription(order),
                    order.getAssigneeDisplay() == null ? "" : order.getAssigneeDisplay(),
                    order.getStatus().getLabel(),
                    total,
                    paid,
                    remaining));
            totalAmount = totalAmount.add(total);
            totalPaid = totalPaid.add(paid);
            totalRemaining = totalRemaining.add(remaining);
        }
        return new DailyReportView(date, rows, scale(totalAmount), scale(totalPaid),
                scale(totalRemaining));
    }

    public byte[] excel(DailyReportView view) {
        List<String> headers = List.of(
                "Fiş No", "Açılış", "Müşteri", "Telefon", "Plaka", "Araç",
                "Şikâyet", "Yapılan İş", "İlgilenen", "Durum",
                "Toplam", "Tahsilat", "Kalan");
        List<List<Object>> rows = view.rows().stream()
                .map(row -> List.<Object>of(
                        row.orderNo(),
                        row.openedAt() == null ? "" : TIME.format(row.openedAt()),
                        row.customerName(),
                        row.phone(),
                        row.plate(),
                        row.vehicle(),
                        row.complaint(),
                        row.workDone(),
                        row.assignedName(),
                        row.statusLabel(),
                        row.total(),
                        row.paid(),
                        row.remaining()))
                .toList();
        List<String> info = List.of(
                "Tarih: " + DATE.format(view.date()),
                "Toplam iş: " + view.rows().size(),
                "İş emri toplamı: " + CsvUtil.money(view.totalAmount()) + " ₺"
                        + " · Tahsilat: " + CsvUtil.money(view.totalPaid()) + " ₺"
                        + " · Kalan: " + CsvUtil.money(view.totalRemaining()) + " ₺",
                "Kalan = iş emri toplamı - tahsilat. Cari borç yalnızca tamamlanan işlerde oluşur.");
        return ExcelUtil.toXlsx("Günlük İşler", "Günlük İş Dökümü", info, headers, rows);
    }

    private String vehicleName(WorkOrder order) {
        String brand = order.getVehicle().getBrand();
        String model = order.getVehicle().getModel();
        String value = ((brand == null ? "" : brand) + " " + (model == null ? "" : model)).trim();
        return value;
    }

    private String workDescription(WorkOrder order) {
        if (order.getWorkDone() != null && !order.getWorkDone().isBlank()) {
            return order.getWorkDone().trim();
        }
        String items = order.getItems().stream()
                .map(WorkOrderItem::getDescription)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(", "));
        return items.isBlank() ? "—" : items;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
