package tr.com.servisdefteri.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemplateSmokeIT extends BusinessTestSupport {

    private AppUserPrincipal admin;

    @BeforeEach
    void setUpData() {
        User user = createUser("patron", "İşletme Sahibi", Role.PATRON);
        admin = principal(user);
    }

    @Test
    void allMainPagesRenderWithoutLazyLoadingOrTemplateErrors() throws Exception {
        Customer customer = createCustomer("Görsel Test Müşteri", "0555 123 45 67");
        Vehicle vehicle = createVehicle(customer, "34TEST123");
        WorkOrder order = createCompletedOrder(customer, vehicle, "10000.00");
        Payment payment = createPayment(customer, "4000.00");

        String customerUrl = "/customers/" + customer.getId();
        String vehicleUrl = "/vehicles/" + vehicle.getId();
        String orderUrl = "/work-orders/" + order.getId();
        String paymentUrl = "/payments/" + payment.getId();

        mockMvc.perform(get("/").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("İşletme Kokpiti")));

        String[] pages = {
                "/customers",
                "/customers/new",
                customerUrl,
                customerUrl + "/edit",
                "/vehicles",
                "/vehicles/new",
                vehicleUrl,
                vehicleUrl + "/edit",
                "/work-orders",
                "/work-orders/board",
                "/work-orders/new",
                "/work-orders/quick",
                orderUrl,
                orderUrl + "/edit",
                orderUrl + "/print",
                orderUrl + "/intake",
                orderUrl + "/delivery",
                "/appointments",
                "/appointments/new",
                "/appointments/week",
                "/accounts",
                "/accounts/new-debt?customerId=" + customer.getId(),
                "/accounts/customer/" + customer.getId(),
                "/payments",
                "/payments/new",
                "/payments/new?customerId=" + customer.getId(),
                paymentUrl,
                paymentUrl + "/receipt",
                "/reports",
                "/reports/collections",
                "/reports/balances",
                "/reports/work-orders",
                "/reports/service-parts",
                "/reports/aging",
                "/reports/maintenance",
                "/reports/vehicle-history?vehicleId=" + vehicle.getId(),
                "/reports/ledger?customerId=" + customer.getId(),
                "/users",
                "/users/new",
                "/users/" + admin.getId() + "/edit",
                "/users/" + admin.getId() + "/password",
                "/settings",
                "/settings/audit",
                "/search?q=34",
                "/search?q=Görsel"
        };

        for (String page : pages) {
            mockMvc.perform(get(page).with(user(admin)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void csvExportsStartWithUtf8Bom() throws Exception {
        Customer customer = createCustomer("CSV Müşteri", "0555 987 65 43");
        Vehicle vehicle = createVehicle(customer, "34CSV123");
        createCompletedOrder(customer, vehicle, "1500.00");
        createPayment(customer, "500.00");

        MvcResult statement = mockMvc.perform(get("/accounts/customer/" + customer.getId() + "/csv")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] statementBytes = statement.getResponse().getContentAsByteArray();
        assertThat(statementBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(statementBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(statementBytes[2]).isEqualTo((byte) 0xBF);

        MvcResult collections = mockMvc.perform(get("/reports/collections.csv").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(collections.getResponse().getContentAsByteArray()[0]).isEqualTo((byte) 0xEF);
    }

    @Test
    void missingRecordRendersTurkishErrorPage() throws Exception {
        mockMvc.perform(get("/customers/999999").with(user(admin)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kayıt bulunamadı")));
    }

    @Test
    void setupPageRedirectsWhenUsersExist() throws Exception {
        mockMvc.perform(get("/setup"))
                .andExpect(status().is3xxRedirection());
    }
}
