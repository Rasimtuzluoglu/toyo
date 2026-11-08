package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderForm;
import tr.com.servisdefteri.workorder.WorkOrderService;

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
}
