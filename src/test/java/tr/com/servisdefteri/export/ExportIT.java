package tr.com.servisdefteri.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportIT extends BusinessTestSupport {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private User patron;
    private Customer customer;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        customer = createCustomer("Ali Veli", "05551112233");
        vehicle = createVehicle(customer, "34ABC123");
    }

    @Test
    void customerListExportsAsCsvAndExcel() throws Exception {
        MvcResult csv = mockMvc.perform(get("/customers/export.csv").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn();
        assertThat(csv.getResponse().getContentAsByteArray())
                .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("Ali Veli", "05551112233");

        assertExcel(mockMvc.perform(get("/customers/export.xlsx").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX))
                .andReturn());
    }

    @Test
    void vehicleAndWorkOrderListsExportAsCsvAndExcel() throws Exception {
        WorkOrder order = createWorkOrderWithItem(customer, vehicle, "1500.00");

        assertExcel(mockMvc.perform(get("/vehicles/export.xlsx").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());

        MvcResult csv = mockMvc.perform(get("/work-orders/export.csv").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains(order.getOrderNo(), "34ABC123");

        assertExcel(mockMvc.perform(get("/work-orders/export.xlsx").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void paymentListExportsAsCsvAndExcel() throws Exception {
        createCompletedOrder(customer, vehicle, "800.00");
        createPayment(customer, "800.00");

        MvcResult csv = mockMvc.perform(get("/payments/export.csv").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("Ali Veli");

        assertExcel(mockMvc.perform(get("/payments/export.xlsx").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void reportAndStatementExportsProduceExcel() throws Exception {
        createCompletedOrder(customer, vehicle, "950.00");

        assertExcel(mockMvc.perform(get("/reports/balances.xlsx").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());
        assertExcel(mockMvc.perform(get("/reports/collections.xlsx").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());
        assertExcel(mockMvc.perform(get("/reports/work-orders.xlsx").with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());
        assertExcel(mockMvc.perform(get("/reports/ledger.xlsx")
                        .param("customerId", customer.getId().toString())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());
        assertExcel(mockMvc.perform(get("/accounts/customer/{id}/xlsx", customer.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void servisUserCannotExportPayments() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);

        mockMvc.perform(get("/payments/export.csv").with(user(principal(servis))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/reports/collections.xlsx").with(user(principal(servis))))
                .andExpect(status().isForbidden());
    }

    private void assertExcel(MvcResult result) {
        byte[] content = result.getResponse().getContentAsByteArray();
        assertThat(content).hasSizeGreaterThan(500);
        assertThat(content[0]).isEqualTo((byte) 'P');
        assertThat(content[1]).isEqualTo((byte) 'K');
    }
}
