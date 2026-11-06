package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import tr.com.servisdefteri.appointment.Appointment;
import tr.com.servisdefteri.appointment.AppointmentForm;
import tr.com.servisdefteri.appointment.AppointmentService;
import tr.com.servisdefteri.appointment.AppointmentStatus;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.report.ReportService;
import tr.com.servisdefteri.settings.BusinessSettingsService;
import tr.com.servisdefteri.settings.BusinessSettings;
import tr.com.servisdefteri.settings.BusinessSettingsForm;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsabilityIT extends BusinessTestSupport {

    @Autowired
    private QuickWorkOrderService quickWorkOrderService;

    @Autowired
    private FormTokenService formTokenService;

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
    void newWorkOrderPagePrefillsPlateFromLink() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);

        mockMvc.perform(get("/work-orders/new").param("plate", "34 PLK 001")
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-quick-plate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("34 PLK 001")));
    }

    @Test
    void vehicleSuggestionsMatchPlateCustomerAndPhone() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer customer = createCustomer("Tahmin Müşteri", "0555 197 97 97");
        createVehicle(customer, "34 SUG 001");

        mockMvc.perform(get("/vehicles/suggest").param("q", "SUG")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].plate").value("34SUG001"))
                .andExpect(jsonPath("$[0].customerName").value("Tahmin Müşteri"));

        mockMvc.perform(get("/vehicles/suggest").param("q", "34 SUG")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plate").value("34SUG001"));

        mockMvc.perform(get("/vehicles/suggest").param("q", "0555 197")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plate").value("34SUG001"));
    }

    @Test
    void newWorkOrderWithoutOrderNumberIsRejected() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        long before = count("select count(*) from work_orders");

        mockMvc.perform(post("/work-orders/new")
                        .session(session)
                        .with(user(principal(servis)))
                        .with(csrf())
                        .param("formToken", token)
                        .param("plate", "34 NON 001")
                        .param("customerName", "Numarasız Müşteri")
                        .param("phone", "0555 198 98 98")
                        .param("complaint", "Kontrol")
                        .param("serviceDescription", "Kontrol / İnceleme"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "İş emri numarası zorunludur.")));

        assertThat(count("select count(*) from work_orders")).isEqualTo(before);
    }

    @Test
    void patronCanSoftDeleteCustomerAndItsVehicles() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Silinecek Müşteri", "0555 199 99 99");
        Vehicle vehicle = createVehicle(customer, "34 DEL 001");

        mockMvc.perform(post("/customers/{id}/delete", customer.getId())
                        .with(user(principal(patron)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers?active=true"));

        assertThat(customerService.findById(customer.getId()).isActive()).isFalse();
        assertThat(vehicleService.findById(vehicle.getId()).isActive()).isFalse();

        mockMvc.perform(get("/customers").with(user(principal(patron))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Silinecek Müşteri"))));
        mockMvc.perform(get("/customers").param("active", "all").with(user(principal(patron))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Silinecek Müşteri")));

        mockMvc.perform(post("/customers/{id}/activate", customer.getId())
                        .with(user(principal(patron)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers?active=all"));

        assertThat(customerService.findById(customer.getId()).isActive()).isTrue();
        assertThat(vehicleService.findById(vehicle.getId()).isActive()).isTrue();
    }

    @Test
    void patronCanSoftDeleteVehicle() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Araç Silme Müşteri", "0555 200 00 00");
        Vehicle vehicle = createVehicle(customer, "34 DEL 002");

        mockMvc.perform(post("/vehicles/{id}/delete", vehicle.getId())
                        .with(user(principal(patron)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vehicles?active=true"));

        assertThat(vehicleService.findById(vehicle.getId()).isActive()).isFalse();
    }

    @Test
    void orderNumberAvailabilityEndpointDetectsDuplicates() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Numara Kontrol Müşteri", "0555 201 01 01");
        Vehicle vehicle = createVehicle(customer, "34 AVL 001");
        tr.com.servisdefteri.workorder.WorkOrderForm form =
                new tr.com.servisdefteri.workorder.WorkOrderForm();
        form.setOrderNo("AVL-2026-001");
        form.setCustomerId(customer.getId());
        form.setVehicleId(vehicle.getId());
        form.setOpenedAt(LocalDateTime.now().minusMinutes(5));
        workOrderService.create(form);

        mockMvc.perform(get("/work-orders/order-no/available")
                        .param("orderNo", "avl-2026-001")
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/work-orders/order-no/available")
                        .param("orderNo", "AVL-2026-002")
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void singleOpenDebtIsPrefilledOnPaymentPage() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Tek Borç Müşteri", "0555 202 02 02");
        Vehicle vehicle = createVehicle(customer, "34 PAY 001");
        createCompletedOrder(customer, vehicle, "1200.00");

        mockMvc.perform(get("/payments/new")
                        .param("customerId", customer.getId().toString())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"1200.00\"")));
    }

    @Test
    void newWorkOrderPageIsSingleScreenAndOnlySaves() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);

        mockMvc.perform(get("/work-orders/new").with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Müşteri ne dedi?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Yapılacak iş")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kim ilgileniyor?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gelişmiş alanlar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kaydet")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Kaydet ve Tamamla"))));
    }

    @Test
    void newWorkOrderSavesAssignedUserAndServiceWithoutCompleting() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);

        mockMvc.perform(post("/work-orders/new")
                        .session(session)
                        .with(user(principal(servis)))
                        .with(csrf())
                        .param("formToken", token)
                        .param("orderNo", "YNI-2026-001")
                        .param("plate", "34 YNI 001")
                        .param("customerName", "Yeni Akış Müşteri")
                        .param("phone", "0555 196 96 96")
                        .param("complaint", "Fren yaparken ses geliyor")
                        .param("serviceDescription", "Fren bakımı")
                        .param("assignedUserId", servis.getId().toString())
                        .param("notes", "Ekip notu")
                        .param("completeNow", "true"))
                .andExpect(status().is3xxRedirection());

        Long orderId = jdbcTemplate.queryForObject("""
                select w.id from work_orders w
                join vehicles v on v.id = w.vehicle_id
                where v.plate_normalized = '34YNI001'
                order by w.id desc limit 1
                """, Long.class);
        WorkOrder order = workOrderService.findById(orderId);

        assertThat(order.getStatus().name()).isEqualTo("BEKLIYOR");
        assertThat(order.getAssignedUser().getId()).isEqualTo(servis.getId());
        assertThat(order.getComplaint()).isEqualTo("Fren yaparken ses geliyor");
        assertThat(order.getNotes()).isEqualTo("Ekip notu");
        assertThat(order.getItems()).anySatisfy(item ->
                assertThat(item.getDescription()).isEqualTo("Fren bakımı"));
    }

    @Test
    void quickPageOffersPlateLookupAndServiceShortcuts() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);

        mockMvc.perform(get("/work-orders/quick")
                        .param("plate", "34 LKP 001")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-quick-plate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-quick-service")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Periyodik Bakım")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kaydet ve Tamamla")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("34 LKP 001")));
    }

    @Test
    void vehicleLookupReturnsRegisteredVehicleAndCustomer() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer customer = createCustomer("Plaka Sorgu Müşteri", "0555 333 44 55");
        createVehicle(customer, "34 LKP 001");

        mockMvc.perform(get("/vehicles/lookup")
                        .param("plate", "34 lkp 001")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.plate").value("34LKP001"))
                .andExpect(jsonPath("$.customerName").value("Plaka Sorgu Müşteri"))
                .andExpect(jsonPath("$.mileage").value(1000));
    }

    @Test
    void vehicleLookupReturnsNotFoundForUnknownPlate() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);

        mockMvc.perform(get("/vehicles/lookup")
                        .param("plate", "99 XYZ 999")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void quickSuccessBannerShowsNextActions() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Hızlı Banner Müşteri", "0555 909 90 90");
        Vehicle vehicle = createVehicle(customer, "34 BNR 001");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1200.00");

        mockMvc.perform(get("/work-orders/{id}", order.getId())
                        .flashAttr("quickCreated", true)
                        .flashAttr("quickCompleted", true)
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Yeni İş Emri")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tahsilat Al")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Teslim Tutanağı")));
    }

    @Test
    void boardSearchFiltersCardsAndOffersMobileTabs() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer first = createCustomer("Pano Müşteri A", "0555 191 91 91");
        Vehicle firstVehicle = createVehicle(first, "34 PNA 001");
        createOpenOrder(first, firstVehicle);
        Customer second = createCustomer("Pano Müşteri B", "0555 192 92 92");
        Vehicle secondVehicle = createVehicle(second, "34 PNB 002");
        createOpenOrder(second, secondVehicle);

        mockMvc.perform(get("/work-orders/board")
                        .param("q", "PNA")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("34PNA001")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("34PNB002"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-kanban-tab")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-kanban-search")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-kanban-density")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-kanban-collapse")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2+ gün")));
    }

    @Test
    void boardAgeFilterSeparatesOldAndTodayOrders() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer todayCustomer = createCustomer("Bugün Pano Müşteri", "0555 194 94 94");
        Vehicle todayVehicle = createVehicle(todayCustomer, "34 TDO 001");
        createOpenOrder(todayCustomer, todayVehicle);

        Customer oldCustomer = createCustomer("Eski Pano Müşteri", "0555 195 95 95");
        Vehicle oldVehicle = createVehicle(oldCustomer, "34 OLD 001");
        tr.com.servisdefteri.workorder.WorkOrderForm oldForm =
                new tr.com.servisdefteri.workorder.WorkOrderForm();
        oldForm.setCustomerId(oldCustomer.getId());
        oldForm.setVehicleId(oldVehicle.getId());
        oldForm.setOpenedAt(LocalDateTime.now().minusDays(3));
        oldForm.setComplaint("Eski açık iş");
        workOrderService.create(oldForm);

        mockMvc.perform(get("/work-orders/board")
                        .param("age", "old")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("34OLD001")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("34TDO001"))));
    }

    @Test
    void appointmentStartMarksArrivedAndOpensQuickWorkOrder() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer customer = createCustomer("Randevu Akış Müşteri", "0555 193 93 93");
        Vehicle vehicle = createVehicle(customer, "34 APT 001");

        AppointmentForm appointmentForm = new AppointmentForm();
        appointmentForm.setCustomerId(customer.getId());
        appointmentForm.setVehicleId(vehicle.getId());
        appointmentForm.setStartsAt(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0)
                .withSecond(0).withNano(0));
        Appointment appointment = appointmentService.create(appointmentForm);

        mockMvc.perform(post("/appointments/{id}/start", appointment.getId())
                        .with(user(principal(servis)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/new?plate=34APT001"));

        assertThat(appointmentService.findById(appointment.getId()).getStatus())
                .isEqualTo(AppointmentStatus.GELDI);
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
    void completeWithPaymentCreatesPaymentAndClosesBalance() {
        Customer customer = createCustomer("Tek Adım Müşteri", "0555 141 41 41");
        Vehicle vehicle = createVehicle(customer, "34 TEK 001");
        WorkOrder order = createWorkOrderWithItem(customer, vehicle, "1200.00");

        runAsSystemPatron(() -> {
            workOrderService.changeStatus(order.getId(), tr.com.servisdefteri.workorder.WorkOrderStatus.ISLEMDE);
            workOrderService.completeWithPayment(order.getId(), PaymentMethod.NAKIT);
        });

        WorkOrder completed = workOrderService.findById(order.getId());
        assertThat(completed.getStatus().name()).isEqualTo("TAMAMLANDI");
        assertThat(completed.isDebited()).isTrue();
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("0.00");

        List<Payment> payments = paymentService.byCustomer(customer.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getMethod()).isEqualTo(PaymentMethod.NAKIT);
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void undoCompletionReversesDebtAndRestoresStatus() {
        Customer customer = createCustomer("Geri Al Müşteri", "0555 151 51 51");
        Vehicle vehicle = createVehicle(customer, "34 GRD 001");
        WorkOrder order = createCompletedOrder(customer, vehicle, "800.00");
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("800.00");

        runAsSystemPatron(() -> workOrderService.undoCompletion(order.getId()));

        WorkOrder restored = workOrderService.findById(order.getId());
        assertThat(restored.getStatus().name()).isEqualTo("ISLEMDE");
        assertThat(restored.isDebited()).isFalse();
        assertThat(restored.getClosedAt()).isNull();
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("0.00");
    }

    @Test
    void reCompletingAfterUndoCreatesFreshDebtAndPayment() {
        Customer customer = createCustomer("Tekrar Tamamla Müşteri", "0555 181 81 81");
        Vehicle vehicle = createVehicle(customer, "34 TKR 001");
        WorkOrder order = createCompletedOrder(customer, vehicle, "600.00");

        runAsSystemPatron(() -> workOrderService.undoCompletion(order.getId()));
        runAsSystemPatron(() -> workOrderService.completeWithPayment(order.getId(), PaymentMethod.NAKIT));

        WorkOrder completed = workOrderService.findById(order.getId());
        assertThat(completed.getStatus().name()).isEqualTo("TAMAMLANDI");
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("0.00");
        assertThat(paymentService.byCustomer(customer.getId())).hasSize(1);
    }

    @Test
    void servisUserCannotCollectPaymentWhileCompleting() {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer customer = createCustomer("Yetki Müşteri", "0555 161 61 61");
        Vehicle vehicle = createVehicle(customer, "34 YET 001");
        WorkOrder order = createWorkOrderWithItem(customer, vehicle, "500.00");

        authenticate(servis);
        try {
            assertThatThrownBy(() -> workOrderService.completeWithPayment(order.getId(), PaymentMethod.NAKIT))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("yetkisi");
        } finally {
            clearAuthentication();
        }

        assertThat(workOrderService.findById(order.getId()).getStatus().name()).isEqualTo("BEKLIYOR");
    }

    @Test
    void completingFromBoardOffersUndoToast() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        Customer customer = createCustomer("Undo Müşteri", "0555 171 71 71");
        Vehicle vehicle = createVehicle(customer, "34 UND 001");
        WorkOrder order = createOpenOrder(customer, vehicle);
        runAsSystemPatron(() -> workOrderService.changeStatus(order.getId(),
                tr.com.servisdefteri.workorder.WorkOrderStatus.ISLEMDE));

        mockMvc.perform(post("/work-orders/{id}/status", order.getId())
                        .param("status", "TAMAMLANDI")
                        .param("returnTo", "board")
                        .with(user(principal(servis)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/board"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                        .attribute("undoOrderId", order.getId()));
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

    @Test
    void brandingSettingsAndLogoCanBeUpdated() throws Exception {
        BusinessSettings original = businessSettingsService.get();
        try {
            BusinessSettingsForm form = new BusinessSettingsForm();
            form.setName("Test Bayi");
            form.setTagline("Test Servis");
            form.setPrimaryColor("#0055aa");
            form.setLogo(new MockMultipartFile("logo", "logo.png", "image/png",
                    "test-logo".getBytes(StandardCharsets.UTF_8)));
            businessSettingsService.update(form);

            BusinessSettings updated = businessSettingsService.get();
            assertThat(updated.getName()).isEqualTo("Test Bayi");
            assertThat(updated.getTagline()).isEqualTo("Test Servis");
            assertThat(updated.getPrimaryColor()).isEqualTo("#0055aa");
            assertThat(updated.getLogoData()).isNotEmpty();

            mockMvc.perform(get("/branding/logo"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/png"));
            mockMvc.perform(get("/branding/theme.css"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/css"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("--sd-accent:#0055aa")));
        } finally {
            BusinessSettingsForm reset = new BusinessSettingsForm();
            reset.setName(original.getName());
            reset.setTagline(original.getTagline());
            reset.setPrimaryColor(original.getPrimaryColor());
            reset.setPhone(original.getPhone());
            reset.setEmail(original.getEmail());
            reset.setAddress(original.getAddress());
            reset.setTaxOffice(original.getTaxOffice());
            reset.setTaxNumber(original.getTaxNumber());
            reset.setRemoveLogo(true);
            businessSettingsService.update(reset);
        }
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
