package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.appointment.Appointment;
import tr.com.servisdefteri.appointment.AppointmentForm;
import tr.com.servisdefteri.appointment.AppointmentService;
import tr.com.servisdefteri.appointment.AppointmentStatus;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.report.ReportService;
import tr.com.servisdefteri.settings.BusinessSettingsService;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.FuelLevel;
import tr.com.servisdefteri.workorder.QuickWorkOrderForm;
import tr.com.servisdefteri.workorder.QuickWorkOrderService;
import tr.com.servisdefteri.workorder.VehicleDeliveryForm;
import tr.com.servisdefteri.workorder.VehicleDocumentService;
import tr.com.servisdefteri.workorder.VehicleIntakeForm;
import tr.com.servisdefteri.workorder.WorkOrder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsabilityIT extends BusinessTestSupport {

    @Autowired
    private QuickWorkOrderService quickWorkOrderService;

    @Autowired
    private VehicleDocumentService vehicleDocumentService;

    private static final String TINY_PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private BusinessSettingsService businessSettingsService;

    @Autowired
    private ReportService reportService;

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void quickWorkOrderCreatesCustomerVehicleAndOrderTogether() {
        QuickWorkOrderForm form = quickForm("34 abc 123", "Ayşe Kaya", "0555 111 22 33");
        form.setBrand("Toyota");
        form.setModel("Corolla");
        form.setMileage(12345);
        form.setComplaint("Fren sesi");

        WorkOrder order = quickWorkOrderService.create(form);

        assertThat(order.getOrderNo()).startsWith("IS-");
        assertThat(order.getCustomer().getName()).isEqualTo("Ayşe Kaya");
        assertThat(order.getCustomer().getPhoneNormalized()).isEqualTo("5551112233");
        assertThat(order.getVehicle().getPlateNormalized()).isEqualTo("34ABC123");
        assertThat(order.getVehicle().getBrand()).isEqualTo("Toyota");
        assertThat(order.getMileage()).isEqualTo(12345);
        assertThat(order.getComplaint()).isEqualTo("Fren sesi");
        assertThat(order.getStatus().name()).isEqualTo("BEKLIYOR");
    }

    @Test
    void quickWorkOrderReusesExistingVehicleAndCustomer() {
        QuickWorkOrderForm first = quickForm("34 ABC 123", "Ayşe Kaya", "0555 111 22 33");
        WorkOrder firstOrder = quickWorkOrderService.create(first);

        QuickWorkOrderForm second = quickForm("34ABC123", "Ayşe Kaya", "0555 111 22 33");
        second.setComplaint("Yağ bakımı");
        WorkOrder secondOrder = quickWorkOrderService.create(second);

        assertThat(secondOrder.getVehicle().getId()).isEqualTo(firstOrder.getVehicle().getId());
        assertThat(secondOrder.getCustomer().getId()).isEqualTo(firstOrder.getCustomer().getId());
        assertThat(count("select count(*) from customers")).isEqualTo(1);
        assertThat(count("select count(*) from vehicles")).isEqualTo(1);
        assertThat(count("select count(*) from work_orders")).isEqualTo(2);
    }

    @Test
    void boardStatusChangeRedirectsBackToBoard() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer customer = createCustomer("Board Müşteri", "0555 222 33 44");
        Vehicle vehicle = createVehicle(customer, "34 BRD 001");
        WorkOrder order = createOpenOrder(customer, vehicle);

        mockMvc.perform(post("/work-orders/{id}/status", order.getId())
                        .param("status", "ISLEMDE")
                        .param("returnTo", "board")
                        .with(user(principal(servis)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/board"));

        assertThat(workOrderService.findById(order.getId()).getStatus().name()).isEqualTo("ISLEMDE");
    }

    @Test
    void servisUserLandsOnDailyBoard() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);

        mockMvc.perform(get("/").with(user(principal(servis))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/board"));
    }

    @Test
    void manifestIsPubliclyReadable() throws Exception {
        mockMvc.perform(get("/manifest.json"))
                .andExpect(status().isOk());
    }

    @Test
    void plateLookupPrefillsFormOrOffersQuickCreate() throws Exception {        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Plaka Müşteri", "0555 333 44 55");
        createVehicle(customer, "34 PLK 001");

        mockMvc.perform(get("/work-orders/new").param("plate", "34 PLK 001")
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bulundu")));

        mockMvc.perform(get("/work-orders/new").param("plate", "99 XYZ 999")
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("kayıtlı değil")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hızlı İş Emri Oluştur")));
    }

    @Test
    void workOrderAndReceiptPdfsAreDownloadable() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("PDF Müşteri", "0555 444 55 66");
        Vehicle vehicle = createVehicle(customer, "34 PDF 001");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1200.00");
        Payment payment = createPayment(customer, "1200.00");

        assertPdf(mockMvc.perform(get("/work-orders/{id}/pdf", order.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn());

        assertPdf(mockMvc.perform(get("/payments/{id}/receipt.pdf", payment.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn());
    }

    @Test
    void intakeAndDeliveryDocumentsCanBeSavedAndDownloadedAsPdf() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Kabul Müşteri", "0555 666 77 88");
        Vehicle vehicle = createVehicle(customer, "34 KBL 001");
        WorkOrder order = createCompletedOrder(customer, vehicle, "2200.00");

        VehicleIntakeForm intakeForm = new VehicleIntakeForm();
        intakeForm.setFuelLevel(FuelLevel.CEYREK);
        intakeForm.setDamageNotes("Sol ön çamurlukta çizik var.");
        intakeForm.setValuables("Ruhsat ve bakım kitapçığı torpido içinde.");
        intakeForm.setSignatureData(TINY_PNG);
        vehicleDocumentService.saveIntake(order.getId(), intakeForm);

        assertThat(vehicleDocumentService.findIntake(order.getId()).getFuelLevel())
                .isEqualTo(FuelLevel.CEYREK);
        assertThat(vehicleDocumentService.findIntake(order.getId()).getSignatureData())
                .isEqualTo(TINY_PNG);
        assertPdf(mockMvc.perform(get("/work-orders/{id}/intake.pdf", order.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn());
        mockMvc.perform(get("/work-orders/{id}/intake/print", order.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ARAÇ KABUL FORMU")));

        VehicleDeliveryForm deliveryForm = new VehicleDeliveryForm();
        deliveryForm.setReceiverName("Kabul Müşteri");
        deliveryForm.setMileage(1500);
        deliveryForm.setFuelLevel(FuelLevel.YARIM);
        deliveryForm.setNotes("Araç yıkanmış olarak teslim edildi.");
        vehicleDocumentService.saveDelivery(order.getId(), deliveryForm);

        assertThat(vehicleDocumentService.findDelivery(order.getId()).getReceiverName())
                .isEqualTo("Kabul Müşteri");
        assertPdf(mockMvc.perform(get("/work-orders/{id}/delivery.pdf", order.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn());
        mockMvc.perform(get("/work-orders/{id}/delivery/print", order.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ARAÇ TESLİM TUTANAĞI")));
    }

    @Test
    void deliveryDocumentRequiresCompletedWorkOrder() {
        Customer customer = createCustomer("Teslim Müşteri", "0555 777 88 99");
        Vehicle vehicle = createVehicle(customer, "34 TSL 001");
        WorkOrder openOrder = createOpenOrder(customer, vehicle);

        VehicleDeliveryForm form = new VehicleDeliveryForm();
        form.setReceiverName("Teslim Müşteri");
        form.setFuelLevel(FuelLevel.YARIM);

        assertThatThrownBy(() -> vehicleDocumentService.saveDelivery(openOrder.getId(), form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tamamlandı");
    }

    @Test
    void dailyAppointmentsCanBeCreatedAndCancelled() {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Randevu Müşteri", "0555 888 99 00");
        Vehicle vehicle = createVehicle(customer, "34 RND 001");
        LocalDateTime startsAt = LocalDate.now().plusDays(1).atTime(10, 30);

        AppointmentForm form = new AppointmentForm();
        form.setCustomerId(customer.getId());
        form.setVehicleId(vehicle.getId());
        form.setStartsAt(startsAt);
        form.setNotes("Periyodik bakım");
        Appointment appointment = appointmentService.create(form);

        assertThat(appointmentService.day(startsAt.toLocalDate()))
                .extracting(Appointment::getId)
                .contains(appointment.getId());
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PLANLANDI);

        assertThatThrownBy(() -> {
            AppointmentForm duplicate = new AppointmentForm();
            duplicate.setCustomerId(customer.getId());
            duplicate.setVehicleId(vehicle.getId());
            duplicate.setStartsAt(startsAt);
            appointmentService.create(duplicate);
        }).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aynı saatte");

        Appointment cancelled = appointmentService.cancel(appointment.getId());
        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.IPTAL);
    }

    @Test
    void backupDirectoryCanBeConfiguredAndCleared() {
        businessSettingsService.updateBackupDirectory(tempDir.toString());
        assertThat(businessSettingsService.get().getBackupDirectory())
                .isEqualTo(tempDir.toAbsolutePath().normalize().toString());

        businessSettingsService.updateBackupDirectory("  ");
        assertThat(businessSettingsService.get().getBackupDirectory()).isNull();
    }

    @Test
    void maintenanceReminderUsesCompletionDate() {
        Customer customer = createCustomer("Bakım Müşteri", "0555 999 88 77");
        Vehicle vehicle = createVehicle(customer, "34 BKM 001");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1500.00");
        jdbcTemplate.update("update work_orders set closed_at = now() - interval '200 days' where id = ?",
                order.getId());

        assertThat(reportService.maintenanceReminders())
                .anySatisfy(reminder -> {
                    assertThat(reminder.plate()).isEqualTo("34BKM001");
                    assertThat(reminder.daysSince()).isGreaterThanOrEqualTo(199);
                });
    }

    @Test
    void quickWorkOrderCanBeCompletedInOneStep() {
        QuickWorkOrderForm form = quickForm("34 hzl 001", "Hızlı Müşteri", "0555 121 21 21");
        form.setOrderNo("HZL-2026-001");
        form.setServiceDescription("Periyodik bakım");
        form.setServiceAmount(new java.math.BigDecimal("2400.00"));
        form.setCompleteNow(true);

        WorkOrder[] holder = new WorkOrder[1];
        runAsSystemPatron(() -> holder[0] = quickWorkOrderService.create(form));
        WorkOrder order = holder[0];

        assertThat(order.getOrderNo()).isEqualTo("HZL-2026-001");
        assertThat(order.getStatus().name()).isEqualTo("TAMAMLANDI");
        assertThat(order.isDebited()).isTrue();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("2400.00");
        assertThat(accountService.balance(order.getCustomer().getId())).isEqualByComparingTo("2400.00");
    }

    @Test
    void duplicateManualOrderNumberIsRejected() {
        Customer customer = createCustomer("Numara Müşteri", "0555 131 31 31");
        Vehicle vehicle = createVehicle(customer, "34 NUM 001");

        tr.com.servisdefteri.workorder.WorkOrderForm first = new tr.com.servisdefteri.workorder.WorkOrderForm();
        first.setOrderNo("OZ-2026-100");
        first.setCustomerId(customer.getId());
        first.setVehicleId(vehicle.getId());
        first.setOpenedAt(LocalDateTime.now().minusMinutes(5));
        workOrderService.create(first);

        tr.com.servisdefteri.workorder.WorkOrderForm duplicate = new tr.com.servisdefteri.workorder.WorkOrderForm();
        duplicate.setOrderNo("oz-2026-100");
        duplicate.setCustomerId(customer.getId());
        duplicate.setVehicleId(vehicle.getId());
        duplicate.setOpenedAt(LocalDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> workOrderService.create(duplicate))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zaten kullanılıyor");
    }

    private void assertPdf(MvcResult result) {
        byte[] content = result.getResponse().getContentAsByteArray();
        assertThat(content).hasSizeGreaterThan(2000);
        assertThat(new String(content, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    private QuickWorkOrderForm quickForm(String plate, String customerName, String phone) {
        QuickWorkOrderForm form = new QuickWorkOrderForm();
        form.setPlate(plate);
        form.setCustomerName(customerName);
        form.setPhone(phone);
        return form;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
