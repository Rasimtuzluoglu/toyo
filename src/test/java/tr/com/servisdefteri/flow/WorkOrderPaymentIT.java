package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkOrderPaymentIT extends BusinessTestSupport {

    @Autowired
    private FormTokenService formTokenService;

    @Autowired
    private AccountService accountService;

    @Test
    void servisCanTakePartialPaymentForWorkOrder() throws Exception {
        User servis = createUser("servis", "Servis", Role.SERVIS);
        Customer customer = createCustomer("Servis Tahsilat Müşteri", "0555 720 00 01");
        Vehicle vehicle = createVehicle(customer, "34 PAY 101");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1000.00");

        mockMvc.perform(get("/work-orders/{id}", order.getId())
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tahsilat Al")));

        mockMvc.perform(get("/work-orders/{id}/payment", order.getId())
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kalan")));

        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        mockMvc.perform(post("/work-orders/{id}/payment", order.getId())
                        .session(session)
                        .with(user(principal(servis)))
                        .with(csrf())
                        .param("formToken", token)
                        .param("amount", "400.00")
                        .param("method", "NAKIT")
                        .param("description", "Servis tahsilatı"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/" + order.getId()));

        assertThat(accountService.workOrderDebt(order.getId()).remaining())
                .isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(count("select count(*) from payments where customer_id = ?", customer.getId()))
                .isEqualTo(1);

        // Muhasebe kalanı daha sonra tahsil eder.
        createPayment(customer, "600.00");
        assertThat(accountService.workOrderDebt(order.getId()).remaining())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(count("select count(*) from payments where customer_id = ?", customer.getId()))
                .isEqualTo(2);
    }

    @Test
    void servisCannotUseGeneralPaymentModule() throws Exception {
        User servis = createUser("servis", "Servis", Role.SERVIS);

        mockMvc.perform(get("/payments").with(user(principal(servis))))
                .andExpect(status().isForbidden());
    }

    @Test
    void overpaymentIsRejected() throws Exception {
        User servis = createUser("servis", "Servis", Role.SERVIS);
        Customer customer = createCustomer("Fazla Ödeme Müşteri", "0555 720 00 02");
        Vehicle vehicle = createVehicle(customer, "34 PAY 102");
        WorkOrder order = createCompletedOrder(customer, vehicle, "500.00");

        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        mockMvc.perform(post("/work-orders/{id}/payment", order.getId())
                        .session(session)
                        .with(user(principal(servis)))
                        .with(csrf())
                        .param("formToken", token)
                        .param("amount", "700.00")
                        .param("method", "NAKIT"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fazla olamaz")));

        assertThat(count("select count(*) from payments where customer_id = ?", customer.getId()))
                .isZero();
    }

    @Test
    void paymentPageIsNotAvailableForOpenWorkOrder() throws Exception {
        User servis = createUser("servis", "Servis", Role.SERVIS);
        Customer customer = createCustomer("Açık İş Müşteri", "0555 720 00 03");
        Vehicle vehicle = createVehicle(customer, "34 PAY 103");
        WorkOrder order = createOpenOrder(customer, vehicle);

        mockMvc.perform(get("/work-orders/{id}/payment", order.getId())
                        .with(user(principal(servis))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/" + order.getId()));
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
