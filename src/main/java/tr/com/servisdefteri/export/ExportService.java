package tr.com.servisdefteri.export;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.common.CsvUtil;
import tr.com.servisdefteri.common.ExcelUtil;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.customer.CustomerType;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentService;
import tr.com.servisdefteri.payment.PaymentStatus;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderService;
import tr.com.servisdefteri.workorder.WorkOrderStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Liste ekranlarinin filtrelenmis verisini CSV ve Excel olarak disa aktarir.
 * Tum okuma islemleri tek bir salt-okunur islem (transaction) icinde yapilir.
 */
@Service
public class ExportService {

    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final WorkOrderService workOrderService;
    private final PaymentService paymentService;

    public ExportService(CustomerService customerService, VehicleService vehicleService,
                         WorkOrderService workOrderService, PaymentService paymentService) {
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.workOrderService = workOrderService;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public byte[] customersCsv(String q, Boolean active, CustomerType type) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Müşteri", "Tür", "Telefon", "E-posta", "Adres",
                "Vergi Dairesi", "Vergi No", "Durum"));
        for (Customer customer : customerService.listForExport(q, active, type)) {
            sb.append(CsvUtil.row(customer.getName(),
                    customer.getType() == null ? "" : customer.getType().getLabel(),
                    customer.getPhone(), customer.getEmail(), customer.getAddress(),
                    customer.getTaxOffice(), customer.getTaxNumber(),
                    customer.isActive() ? "Aktif" : "Pasif"));
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] customersExcel(String q, Boolean active, CustomerType type) {
        List<List<Object>> rows = new ArrayList<>();
        for (Customer customer : customerService.listForExport(q, active, type)) {
            rows.add(row(customer.getName(),
                    customer.getType() == null ? "" : customer.getType().getLabel(),
                    customer.getPhone(), customer.getEmail(), customer.getAddress(),
                    customer.getTaxOffice(), customer.getTaxNumber(),
                    customer.isActive() ? "Aktif" : "Pasif"));
        }
        return ExcelUtil.toXlsx("Müşteriler", "Müşteri Listesi", null,
                List.of("Müşteri", "Tür", "Telefon", "E-posta", "Adres",
                        "Vergi Dairesi", "Vergi No", "Durum"), rows);
    }

    @Transactional(readOnly = true)
    public byte[] vehiclesCsv(String q, Boolean active, Long customerId) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Plaka", "Marka", "Model", "Yıl", "Müşteri", "Kilometre",
                "Şasi No", "Durum"));
        for (Vehicle vehicle : vehicleService.listForExport(q, active, customerId)) {
            sb.append(CsvUtil.row(vehicle.getPlate(), vehicle.getBrand(), vehicle.getModel(),
                    vehicle.getModelYear(), vehicle.getCustomer().getName(), vehicle.getCurrentMileage(),
                    vehicle.getVin(), vehicle.isActive() ? "Aktif" : "Pasif"));
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] vehiclesExcel(String q, Boolean active, Long customerId) {
        List<List<Object>> rows = new ArrayList<>();
        for (Vehicle vehicle : vehicleService.listForExport(q, active, customerId)) {
            rows.add(row(vehicle.getPlate(), vehicle.getBrand(), vehicle.getModel(),
                    vehicle.getModelYear(), vehicle.getCustomer().getName(), vehicle.getCurrentMileage(),
                    vehicle.getVin(), vehicle.isActive() ? "Aktif" : "Pasif"));
        }
        return ExcelUtil.toXlsx("Araçlar", "Araç Listesi", null,
                List.of("Plaka", "Marka", "Model", "Yıl", "Müşteri", "Kilometre",
                        "Şasi No", "Durum"), rows);
    }

    @Transactional(readOnly = true)
    public byte[] workOrdersCsv(WorkOrderStatus status, String q, Long customerId, Long vehicleId,
                                LocalDate start, LocalDate end) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("İş Emri", "Açılış", "Müşteri", "Araç", "Durum",
                "İşçilik", "Parça", "Toplam"));
        for (WorkOrder order : workOrderService.listForExport(status, customerId, vehicleId, start, end, q)) {
            sb.append(CsvUtil.row(order.getOrderNo(),
                    order.getOpenedAt() == null ? "" : order.getOpenedAt().toLocalDate(),
                    order.getCustomer().getName(), order.getVehicle().getPlate(),
                    order.getStatus().getLabel(), CsvUtil.money(order.getLaborTotal()),
                    CsvUtil.money(order.getPartsTotal()), CsvUtil.money(order.getTotalAmount())));
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] workOrdersExcel(WorkOrderStatus status, String q, Long customerId, Long vehicleId,
                                  LocalDate start, LocalDate end) {
        List<List<Object>> rows = new ArrayList<>();
        for (WorkOrder order : workOrderService.listForExport(status, customerId, vehicleId, start, end, q)) {
            rows.add(row(order.getOrderNo(),
                    order.getOpenedAt() == null ? "" : order.getOpenedAt().toLocalDate(),
                    order.getCustomer().getName(), order.getVehicle().getPlate(),
                    order.getStatus().getLabel(), order.getLaborTotal(),
                    order.getPartsTotal(), order.getTotalAmount()));
        }
        return ExcelUtil.toXlsx("İş Emirleri", "İş Emirleri", null,
                List.of("İş Emri", "Açılış", "Müşteri", "Araç", "Durum",
                        "İşçilik", "Parça", "Toplam"), rows);
    }

    @Transactional(readOnly = true)
    public byte[] paymentsCsv(Long customerId, PaymentMethod method, PaymentStatus status,
                              LocalDate start, LocalDate end, String q) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Makbuz No", "Tarih", "Müşteri", "Ödeme Yöntemi", "Tutar",
                "Durum", "Açıklama"));
        for (Payment payment : paymentService.listForExport(customerId, method, status, start, end, q)) {
            sb.append(CsvUtil.row(payment.getReceiptNo(), payment.getPaymentDate(),
                    payment.getCustomer().getName(),
                    payment.getMethod() == null ? "" : payment.getMethod().getLabel(),
                    CsvUtil.money(payment.getAmount()),
                    payment.getStatus() == null ? "" : payment.getStatus().getLabel(),
                    payment.getDescription()));
        }
        return CsvUtil.toUtf8Bom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] paymentsExcel(Long customerId, PaymentMethod method, PaymentStatus status,
                                LocalDate start, LocalDate end, String q) {
        List<List<Object>> rows = new ArrayList<>();
        for (Payment payment : paymentService.listForExport(customerId, method, status, start, end, q)) {
            rows.add(row(payment.getReceiptNo(), payment.getPaymentDate(),
                    payment.getCustomer().getName(),
                    payment.getMethod() == null ? "" : payment.getMethod().getLabel(),
                    payment.getAmount(),
                    payment.getStatus() == null ? "" : payment.getStatus().getLabel(),
                    payment.getDescription()));
        }
        return ExcelUtil.toXlsx("Tahsilatlar", "Tahsilat Listesi", null,
                List.of("Makbuz No", "Tarih", "Müşteri", "Ödeme Yöntemi", "Tutar",
                        "Durum", "Açıklama"), rows);
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
