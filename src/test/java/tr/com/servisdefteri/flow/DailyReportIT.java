package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.report.DailyReportRow;
import tr.com.servisdefteri.report.DailyReportService;
import tr.com.servisdefteri.report.DailyReportView;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderForm;
import tr.com.servisdefteri.workorder.WorkOrderItemForm;
import tr.com.servisdefteri.workorder.WorkOrderItemType;
import tr.com.servisdefteri.workorder.WorkOrderService;
import tr.com.servisdefteri.workorder.WorkOrderStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyReportIT extends BusinessTestSupport {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private DailyReportService dailyReportService;

    @Test
    void listsOnlyWorkOrdersOpenedOnSelectedDay() throws Exception {
        User servis = createUser("servis", "Servis", Role.SERVIS);
        Customer customer = createCustomer("Günlük Müşteri", "0555 710 00 01");
        Vehicle vehicle = createVehicle(customer, "34 DLY 001");
        WorkOrder today = order(customer, vehicle, LocalDateTime.now().withHour(10).withMinute(0));
        WorkOrder old = order(customer, vehicle, LocalDateTime.now().minusDays(3).withHour(10).withMinute(0));

        mockMvc.perform(get("/daily-report").with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(today.getOrderNo())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("daily-mobile-card")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("d-none d-md-block")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(old.getOrderNo()))));

        mockMvc.perform(get("/daily-report")
                        .param("date", LocalDate.now().minusDays(3).toString())
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(old.getOrderNo())))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(today.getOrderNo()))));
    }

    @Test
    void remainingIncludesOpenWorkOrdersAndMatchesSummaryTotals() {
        createUser("servis", "Servis", Role.SERVIS);
        Customer customer = createCustomer("Günlük Toplam Müşteri", "0555 710 00 03");
        Vehicle vehicle = createVehicle(customer, "34 DLY 003");
        LocalDateTime openedAt = LocalDate.now().atTime(12, 0);

        WorkOrder open = orderWithAmount(customer, vehicle, openedAt, "500.00", false);
        WorkOrder completed = orderWithAmount(customer, vehicle, openedAt, "1000.00", true);
        createPayment(customer, "400.00");

        DailyReportView view = dailyReportService.load(LocalDate.now());
        DailyReportRow openRow = view.rows().stream()
                .filter(row -> row.orderNo().equals(open.getOrderNo()))
                .findFirst().orElseThrow();
        DailyReportRow completedRow = view.rows().stream()
                .filter(row -> row.orderNo().equals(completed.getOrderNo()))
                .findFirst().orElseThrow();

        assertThat(openRow.remaining()).isEqualByComparingTo("500.00");
        assertThat(completedRow.paid()).isEqualByComparingTo("400.00");
        assertThat(completedRow.remaining()).isEqualByComparingTo("600.00");
        assertThat(view.totalAmount()).isEqualByComparingTo("1500.00");
        assertThat(view.totalPaid()).isEqualByComparingTo("400.00");
        assertThat(view.totalRemaining()).isEqualByComparingTo("1100.00");
    }

    @Test
    void exportsDailyReportAsExcelAndPdf() throws Exception {
        User servis = createUser("servis", "Servis", Role.SERVIS);
        Customer customer = createCustomer("Dışa Aktarım Müşteri", "0555 710 00 02");
        Vehicle vehicle = createVehicle(customer, "34 DLY 002");
        order(customer, vehicle, LocalDateTime.now().withHour(11).withMinute(0));

        MvcResult excel = mockMvc.perform(get("/daily-report/export.xlsx")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        assertThat(excel.getResponse().getContentAsByteArray().length).isGreaterThan(1000);

        MvcResult pdf = mockMvc.perform(get("/daily-report/export.pdf")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andReturn();
        byte[] body = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    private WorkOrder order(Customer customer, Vehicle vehicle, LocalDateTime openedAt) {
        WorkOrderForm form = new WorkOrderForm();
        form.setOrderNo("DR-" + System.nanoTime());
        form.setCustomerId(customer.getId());
        form.setVehicleId(vehicle.getId());
        form.setOpenedAt(openedAt);
        form.setComplaint("Günlük döküm testi");
        return workOrderService.create(form);
    }

    private WorkOrder orderWithAmount(Customer customer, Vehicle vehicle, LocalDateTime openedAt,
                                      String amount, boolean complete) {
        WorkOrder order = order(customer, vehicle, openedAt);
        WorkOrderItemForm item = new WorkOrderItemForm();
        item.setType(WorkOrderItemType.HIZMET);
        item.setDescription("Günlük döküm test işçiliği");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal(amount));
        workOrderService.addItem(order.getId(), item);
        if (complete) {
            runAsSystemPatron(() ->
                    workOrderService.changeStatus(order.getId(), WorkOrderStatus.TAMAMLANDI));
        }
        return workOrderService.findById(order.getId());
    }
}
