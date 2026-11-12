package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurgeIT extends BusinessTestSupport {

    @Test
    void patronPurgesCustomerAndAllLinkedRecords() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Silinecek Müşteri", "0555 700 00 01");
        Vehicle vehicle = createVehicle(customer, "34 PUR 001");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1000.00");
        Payment payment = createPayment(customer, "400.00");

        Customer other = createCustomer("Kalan Müşteri", "0555 700 00 02");
        Vehicle otherVehicle = createVehicle(other, "34 PUR 002");
        WorkOrder otherOrder = createOpenOrder(other, otherVehicle);

        mockMvc.perform(post("/purge/customer/{id}", customer.getId())
                        .with(user(principal(patron)))
                        .with(csrf())
                        .param("confirmation", customer.getName()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers?active=all"));

        assertThat(count("select count(*) from customers where id = ?", customer.getId())).isZero();
        assertThat(count("select count(*) from vehicles where id = ?", vehicle.getId())).isZero();
        assertThat(count("select count(*) from work_orders where id = ?", order.getId())).isZero();
        assertThat(count("select count(*) from work_order_items where work_order_id = ?", order.getId())).isZero();
        assertThat(count("select count(*) from payments where id = ?", payment.getId())).isZero();
        assertThat(count("select count(*) from payment_allocations where payment_id = ?", payment.getId())).isZero();
        assertThat(count("select count(*) from account_transactions where customer_id = ?", customer.getId()))
                .isZero();
        assertThat(count("select count(*) from audit_logs where action = 'CUSTOMER_PURGED'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select details from audit_logs where action = 'CUSTOMER_PURGED'", String.class))
                .doesNotContain(customer.getName());

        assertThat(count("select count(*) from customers where id = ?", other.getId())).isEqualTo(1);
        assertThat(count("select count(*) from vehicles where id = ?", otherVehicle.getId())).isEqualTo(1);
        assertThat(count("select count(*) from work_orders where id = ?", otherOrder.getId())).isEqualTo(1);
    }

    @Test
    void purgeCustomerFailsClearlyWhenTransferredVehicleHasOtherOwnersOrders() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer previousOwner = createCustomer("Eski Sahip", "0555 700 00 10");
        Vehicle vehicle = createVehicle(previousOwner, "34 PUR 010");
        WorkOrder historical = createOpenOrder(previousOwner, vehicle);

        Customer newOwner = createCustomer("Yeni Sahip", "0555 700 00 11");
        jdbcTemplate.update("update vehicles set customer_id = ? where id = ?",
                newOwner.getId(), vehicle.getId());

        mockMvc.perform(post("/purge/customer/{id}", newOwner.getId())
                        .with(user(principal(patron)))
                        .with(csrf())
                        .param("confirmation", newOwner.getName()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers
                        .containsString("başka müşterilere ait iş emirleri")));

        assertThat(count("select count(*) from customers where id = ?", newOwner.getId())).isEqualTo(1);
        assertThat(count("select count(*) from work_orders where id = ?", historical.getId())).isEqualTo(1);
    }

    @Test
    void wrongConfirmationKeepsCustomer() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Onay Müşteri", "0555 700 00 03");

        mockMvc.perform(get("/purge/customer/{id}", customer.getId())
                        .with(user(principal(patron))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kalıcı Sil")));

        mockMvc.perform(post("/purge/customer/{id}", customer.getId())
                        .with(user(principal(patron)))
                        .with(csrf())
                        .param("confirmation", "yanlış isim"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("yazmalısınız")));

        assertThat(count("select count(*) from customers where id = ?", customer.getId())).isEqualTo(1);
    }

    @Test
    void turkishUppercaseConfirmationIsAccepted() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Öztoprak Lojistik A.Ş.", "0555 700 00 08");

        mockMvc.perform(post("/purge/customer/{id}", customer.getId())
                        .with(user(principal(patron)))
                        .with(csrf())
                        .param("confirmation", "ÖZTOPRAK LOJİSTİK A.Ş."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers?active=all"));

        assertThat(count("select count(*) from customers where id = ?", customer.getId())).isZero();
    }

    @Test
    void onlyPatronCanOpenPurgeScreen() throws Exception {
        User muhasebe = createUser("muhasebe", "Muhasebe", Role.MUHASEBE);
        Customer customer = createCustomer("Yetki Müşteri", "0555 700 00 04");

        mockMvc.perform(get("/purge/customer/{id}", customer.getId())
                        .with(user(principal(muhasebe))))
                .andExpect(status().isForbidden());
    }

    @Test
    void purgingWorkOrderDeletesLinkedPayment() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("İş Silme Müşteri", "0555 700 00 05");
        Vehicle vehicle = createVehicle(customer, "34 PUR 005");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1000.00");
        Payment payment = createPayment(customer, "400.00");
        WorkOrder other = createOpenOrder(customer, vehicle);

        mockMvc.perform(post("/purge/work-order/{id}", order.getId())
                        .with(user(principal(patron)))
                        .with(csrf())
                        .param("confirmation", order.getOrderNo()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders"));

        assertThat(count("select count(*) from work_orders where id = ?", order.getId())).isZero();
        assertThat(count("select count(*) from payments where id = ?", payment.getId())).isZero();
        assertThat(count("select count(*) from payment_allocations where payment_id = ?", payment.getId())).isZero();
        assertThat(count("select count(*) from account_transactions where work_order_id = ?", order.getId()))
                .isZero();
        assertThat(count("select count(*) from work_orders where id = ?", other.getId())).isEqualTo(1);
        assertThat(count("select count(*) from vehicles where id = ?", vehicle.getId())).isEqualTo(1);
    }

    @Test
    void purgingPaymentKeepsWorkOrderDebt() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Tahsilat Silme Müşteri", "0555 700 00 06");
        Vehicle vehicle = createVehicle(customer, "34 PUR 006");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1000.00");
        Payment payment = createPayment(customer, "400.00");

        mockMvc.perform(post("/purge/payment/{id}", payment.getId())
                        .with(user(principal(patron)))
                        .with(csrf())
                        .param("confirmation", payment.getReceiptNo()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payments"));

        assertThat(count("select count(*) from payments where id = ?", payment.getId())).isZero();
        assertThat(count("select count(*) from payment_allocations where payment_id = ?", payment.getId())).isZero();
        assertThat(count("select count(*) from account_transactions where payment_id = ?", payment.getId()))
                .isZero();
        assertThat(count("select count(*) from work_orders where id = ?", order.getId())).isEqualTo(1);
    }

    @Test
    void purgingVehicleDeletesItsOrdersAndPayments() throws Exception {
        User patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        Customer customer = createCustomer("Araç Silme Müşteri", "0555 700 00 07");
        Vehicle vehicle = createVehicle(customer, "34 PUR 007");
        Vehicle otherVehicle = createVehicle(customer, "34 PUR 008");
        WorkOrder order = createCompletedOrder(customer, vehicle, "1000.00");
        Payment payment = createPayment(customer, "400.00");
        WorkOrder otherOrder = createOpenOrder(customer, otherVehicle);

        mockMvc.perform(post("/purge/vehicle/{id}", vehicle.getId())
                        .with(user(principal(patron)))
                        .with(csrf())
                        .param("confirmation", vehicle.getPlate()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vehicles?active=all"));

        assertThat(count("select count(*) from vehicles where id = ?", vehicle.getId())).isZero();
        assertThat(count("select count(*) from work_orders where id = ?", order.getId())).isZero();
        assertThat(count("select count(*) from payments where id = ?", payment.getId())).isZero();
        assertThat(count("select count(*) from vehicles where id = ?", otherVehicle.getId())).isEqualTo(1);
        assertThat(count("select count(*) from work_orders where id = ?", otherOrder.getId())).isEqualTo(1);
        assertThat(count("select count(*) from customers where id = ?", customer.getId())).isEqualTo(1);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
