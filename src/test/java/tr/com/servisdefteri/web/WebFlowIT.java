package tr.com.servisdefteri.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebFlowIT extends BusinessTestSupport {

    @Autowired
    private FormTokenService formTokenService;

    @Autowired
    private AccountService accountService;

    @Test
    void customerVehicleWorkOrderAndPaymentFlowWorksThroughHtmlForms() throws Exception {
        createUser("patron", "İşletme Sahibi", Role.PATRON);
        MvcResult loginResult = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "patron")
                        .param("password", "TestPass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        Long customerId = createCustomerThroughForm(session);
        Long vehicleId = createVehicleThroughForm(session, customerId);
        Long orderId = createWorkOrderThroughForm(session, customerId, vehicleId);

        mockMvc.perform(post("/work-orders/{id}/items", orderId).session(session).with(csrf())
                        .param("type", "HIZMET")
                        .param("description", "Fren balata değişimi")
                        .param("quantity", "1")
                        .param("unitPrice", "10000.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/" + orderId));

        mockMvc.perform(post("/work-orders/{id}/status", orderId).session(session).with(csrf())
                        .param("status", "ISLEMDE"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/work-orders/{id}/status", orderId).session(session).with(csrf())
                        .param("status", "TAMAMLANDI"))
                .andExpect(status().is3xxRedirection());

        assertThat(accountService.balance(customerId)).isEqualByComparingTo("10000.00");

        mockMvc.perform(get("/payments/new").session(session).param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("servis bedeli")));

        Long transactionId = accountService.openDebts(customerId).get(0).id();
        String paymentToken = formTokenService.issue(session);
        MvcResult paymentResult = mockMvc.perform(post("/payments").session(session).with(csrf())
                        .param("formToken", paymentToken)
                        .param("customerId", customerId.toString())
                        .param("amount", "4000.00")
                        .param("paymentDate", LocalDate.now().toString())
                        .param("method", "NAKIT")
                        .param("description", "Nakit tahsilat")
                        .param("allocations[0].transactionId", transactionId.toString())
                        .param("allocations[0].amount", "4000.00"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String paymentRedirect = paymentResult.getResponse().getRedirectedUrl();
        assertThat(paymentRedirect).startsWith("/payments/");
        assertThat(accountService.balance(customerId)).isEqualByComparingTo("6000.00");

        mockMvc.perform(get("/accounts/customer/{id}", customerId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("6.000,00")));

        // Ayni form belirteci ikinci kez kullanilamaz; ikinci tahsilat olusmaz.
        mockMvc.perform(post("/payments").session(session).with(csrf())
                        .param("formToken", paymentToken)
                        .param("customerId", customerId.toString())
                        .param("amount", "1000.00")
                        .param("paymentDate", LocalDate.now().toString())
                        .param("method", "NAKIT"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("daha önce gönderilmiş")));
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isEqualTo(1);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    private Long createCustomerThroughForm(MockHttpSession session) throws Exception {
        String token = formTokenService.issue(session);
        MvcResult result = mockMvc.perform(post("/customers").session(session).with(csrf())
                        .param("formToken", token)
                        .param("type", "BIREYSEL")
                        .param("name", "Form Test Müşteri")
                        .param("phone", "0555 321 45 67")
                        .param("email", "form@test.local")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return idFromRedirect(result, "/customers/");
    }

    private Long createVehicleThroughForm(MockHttpSession session, Long customerId) throws Exception {
        String token = formTokenService.issue(session);
        MvcResult result = mockMvc.perform(post("/vehicles").session(session).with(csrf())
                        .param("formToken", token)
                        .param("customerId", customerId.toString())
                        .param("plate", "34 FORM 123")
                        .param("brand", "Renault")
                        .param("model", "Clio")
                        .param("modelYear", "2021")
                        .param("currentMileage", "45000")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return idFromRedirect(result, "/vehicles/");
    }

    private Long createWorkOrderThroughForm(MockHttpSession session, Long customerId, Long vehicleId)
            throws Exception {
        String token = formTokenService.issue(session);
        String openedAt = LocalDateTime.now().minusHours(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        MvcResult result = mockMvc.perform(post("/work-orders").session(session).with(csrf())
                        .param("formToken", token)
                        .param("orderNo", "WEB-" + System.nanoTime())
                        .param("customerId", customerId.toString())
                        .param("vehicleId", vehicleId.toString())
                        .param("openedAt", openedAt)
                        .param("complaint", "Fren sesi")
                        .param("mileage", "45100"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return idFromRedirect(result, "/work-orders/");
    }

    private Long idFromRedirect(MvcResult result, String prefix) {
        String redirect = result.getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith(prefix);
        return Long.valueOf(redirect.substring(prefix.length()));
    }
}
