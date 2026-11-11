package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import tr.com.servisdefteri.account.LedgerStatement;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerForm;
import tr.com.servisdefteri.customer.CustomerType;
import tr.com.servisdefteri.payment.AllocationForm;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentForm;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentStatus;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderBoardRow;
import tr.com.servisdefteri.workorder.WorkOrderForm;
import tr.com.servisdefteri.workorder.WorkOrderItemForm;
import tr.com.servisdefteri.workorder.WorkOrderItemType;
import tr.com.servisdefteri.workorder.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServisDefteriFlowIT extends BusinessTestSupport {

    @Test
    void workOrderCompletionCreatesDebtAndPaymentReducesBalance() {
        Customer customer = createCustomer("Ali Yılmaz", "0532 111 22 33");
        Vehicle vehicle = createVehicle(customer, "34 ABC 123");
        WorkOrder order = createCompletedOrder(customer, vehicle, "10000.00");

        assertThat(order.isDebited()).isTrue();
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("10000.00");
        assertThat(accountService.openDebtTotal(customer.getId())).isEqualByComparingTo("10000.00");

        Payment payment = createPayment(customer, "4000.00");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AKTIF);
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("6000.00");
        assertThat(accountService.openDebtTotal(customer.getId())).isEqualByComparingTo("6000.00");

        LedgerStatement statement = accountService.statement(customer.getId(),
                LocalDate.now().minusDays(10), LocalDate.now());
        assertThat(statement.rows()).hasSize(2);
        assertThat(statement.totalDebit()).isEqualByComparingTo("10000.00");
        assertThat(statement.totalCredit()).isEqualByComparingTo("4000.00");
        assertThat(statement.closingBalance()).isEqualByComparingTo("6000.00");

        assertThat(count("select count(*) from account_transactions where work_order_id = ? and type = 'BORC'",
                order.getId())).isEqualTo(1);
        assertThat(count("select count(*) from payment_allocations where payment_id = ? and cancelled = false",
                payment.getId())).isEqualTo(1);
    }

    @Test
    void sameWorkOrderCannotBeDebitedTwice() {
        Customer customer = createCustomer("Veli Kaya", "0533 222 33 44");
        Vehicle vehicle = createVehicle(customer, "34DEF456");
        WorkOrder order = createCompletedOrder(customer, vehicle, "10000.00");

        accountService.postWorkOrderDebt(order);
        accountService.postWorkOrderDebt(order);

        assertThat(count("select count(*) from account_transactions where work_order_id = ? and type = 'BORC'",
                order.getId())).isEqualTo(1);
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("10000.00");
    }

    @Test
    void paymentAllocationCannotExceedRemainingDebt() {
        Customer customer = createCustomer("Ayşe Demir", "0534 333 44 55");
        Vehicle vehicle = createVehicle(customer, "34GHI789");
        createCompletedOrder(customer, vehicle, "10000.00");

        PaymentForm form = new PaymentForm();
        form.setCustomerId(customer.getId());
        form.setAmount(new BigDecimal("15000.00"));
        form.setPaymentDate(LocalDate.now());
        form.setMethod(PaymentMethod.NAKIT);
        AllocationForm allocation = new AllocationForm();
        allocation.setTransactionId(accountService.openDebts(customer.getId()).get(0).id());
        allocation.setAmount(new BigDecimal("11000.00"));
        form.setAllocations(List.of(allocation));

        assertThatThrownBy(() -> paymentService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kalan borçtan fazla");
        assertThat(count("select count(*) from payments")).isZero();
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("10000.00");
    }

    @Test
    void workOrderCannotBeCreatedWithAnotherCustomersVehicle() {
        Customer first = createCustomer("Birinci Müşteri", "0535 444 55 66");
        Customer second = createCustomer("İkinci Müşteri", "0536 555 66 77");
        Vehicle secondVehicle = createVehicle(second, "34JKL012");

        WorkOrderForm form = new WorkOrderForm();
        form.setOrderNo("TST-" + System.nanoTime());
        form.setCustomerId(first.getId());
        form.setVehicleId(secondVehicle.getId());
        form.setOpenedAt(LocalDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> workOrderService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("bu müşteriye ait değil");
    }

    @Test
    void invalidStatusTransitionIsRejected() {
        Customer customer = createCustomer("Mehmet Şahin", "0537 666 77 88");
        Vehicle vehicle = createVehicle(customer, "34MNO345");
        WorkOrder order = createOpenOrder(customer, vehicle);
        assertThat(order.getStatus()).isEqualTo(WorkOrderStatus.ISLEMDE);

        assertThatThrownBy(() -> workOrderService.changeStatus(order.getId(), WorkOrderStatus.TESLIM_EDILDI))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("geçilemez");
        assertThatThrownBy(() -> workOrderService.changeStatus(order.getId(), WorkOrderStatus.ISLEMDE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("geçilemez");
    }

    @Test
    void cancelledPaymentCreatesReversalAndPreservesHistory() {
        User patron = createUser("patron", "Patron Kullanıcı", Role.PATRON);
        Customer customer = createCustomer("Zeynep Ak", "0538 777 88 99");
        Vehicle vehicle = createVehicle(customer, "34PQR678");
        createCompletedOrder(customer, vehicle, "10000.00");
        Payment payment = createPayment(customer, "4000.00");

        authenticate(patron);
        paymentService.cancel(payment.getId());

        Payment cancelled = paymentService.findById(payment.getId());
        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.IPTAL);
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("10000.00");
        assertThat(accountService.openDebtTotal(customer.getId())).isEqualByComparingTo("10000.00");
        assertThat(accountService.openDebts(customer.getId())).hasSize(1);
        assertThat(count("select count(*) from account_transactions where payment_id = ? and type = 'ALACAK'",
                payment.getId())).isEqualTo(1);
        assertThat(count("select count(*) from account_transactions where reversal_of_id is not null"))
                .isEqualTo(1);
        assertThat(count("select count(*) from payment_allocations where payment_id = ? and cancelled = true",
                payment.getId())).isEqualTo(1);
    }

    @Test
    void servisCannotCancelPaymentButPatronCan() {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        User patron = createUser("patron2", "Patron Kullanıcı", Role.PATRON);
        Customer customer = createCustomer("Burak Yıldız", "0539 888 99 00");
        Vehicle vehicle = createVehicle(customer, "34STU901");
        createCompletedOrder(customer, vehicle, "10000.00");
        Payment payment = createPayment(customer, "4000.00");

        authenticate(servis);
        assertThatThrownBy(() -> paymentService.cancel(payment.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("yetkisi");
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("6000.00");

        authenticate(patron);
        paymentService.cancel(payment.getId());
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("10000.00");
    }

    @Test
    void muhasebeCanCancelPaymentButServisCannotChangeWorkOrderStatus() {
        User muhasebe = createUser("muhasebe", "Muhasebe Kullanıcısı", Role.MUHASEBE);
        User servis = createUser("servis2", "Servis Kullanıcısı", Role.SERVIS);
        Customer customer = createCustomer("Ceren Aydın", "0541 234 56 78");
        Vehicle vehicle = createVehicle(customer, "34MUH123");
        WorkOrder order = createCompletedOrder(customer, vehicle, "5000.00");
        Payment payment = createPayment(customer, "2000.00");

        authenticate(servis);
        assertThatThrownBy(() -> workOrderService.changeStatus(order.getId(), WorkOrderStatus.IPTAL))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("yetkisi");

        authenticate(muhasebe);
        paymentService.cancel(payment.getId());
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("5000.00");
        workOrderService.changeStatus(order.getId(), WorkOrderStatus.IPTAL);
        assertThat(workOrderService.findById(order.getId()).getStatus()).isEqualTo(WorkOrderStatus.IPTAL);
    }

    @Test
    void muhasebeCannotCompleteWorkOrder() {
        User muhasebe = createUser("muhasebe2", "Muhasebe Kullanıcısı", Role.MUHASEBE);
        Customer customer = createCustomer("Deniz Koç", "0542 345 67 89");
        Vehicle vehicle = createVehicle(customer, "34MHS456");
        WorkOrder order = createOpenOrder(customer, vehicle);

        authenticate(muhasebe);
        assertThatThrownBy(() -> workOrderService.changeStatus(order.getId(), WorkOrderStatus.TAMAMLANDI))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("yalnızca");

        assertThat(workOrderService.findById(order.getId()).getStatus()).isEqualTo(WorkOrderStatus.ISLEMDE);
    }

    @Test
    void boardKeepsUnfinishedWorkAndHidesFinishedWorkFromPreviousDays() {
        Customer firstCustomer = createCustomer("Günlük Pano Müşteri", "0543 456 78 90");
        Vehicle firstVehicle = createVehicle(firstCustomer, "34BRD123");
        WorkOrder openOrder = createOpenOrder(firstCustomer, firstVehicle);
        jdbcTemplate.update("update work_orders set opened_at = ? where id = ?",
                LocalDateTime.now().minusDays(3), openOrder.getId());

        Customer secondCustomer = createCustomer("Dün Biten Müşteri", "0544 567 89 01");
        Vehicle secondVehicle = createVehicle(secondCustomer, "34BRD456");
        WorkOrder finishedOrder = createCompletedOrder(secondCustomer, secondVehicle, "750.00");
        jdbcTemplate.update("update work_orders set closed_at = ? where id = ?",
                LocalDateTime.now().minusDays(1), finishedOrder.getId());

        WorkOrderBoardRow openRow = workOrderService.boardOpenRows().stream()
                .filter(row -> row.order().getId().equals(openOrder.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(openRow.daysOpen()).isEqualTo(3);
        assertThat(openRow.getAgeLabel()).isEqualTo("3 günden beri açık");

        assertThat(workOrderService.finishedToday())
                .extracting(WorkOrder::getId)
                .doesNotContain(finishedOrder.getId());

        jdbcTemplate.update("update work_orders set closed_at = ? where id = ?",
                LocalDateTime.now(), finishedOrder.getId());

        assertThat(workOrderService.finishedToday())
                .extracting(WorkOrder::getId)
                .contains(finishedOrder.getId());
    }

    @Test
    void workOrderCancellationIsBlockedWhileActivePaymentExists() {
        User patron = createUser("patron3", "Patron Kullanıcı", Role.PATRON);
        Customer customer = createCustomer("Ece Arslan", "0541 123 45 67");
        Vehicle vehicle = createVehicle(customer, "34VWX234");
        WorkOrder order = createCompletedOrder(customer, vehicle, "10000.00");
        Payment payment = createPayment(customer, "4000.00");

        authenticate(patron);
        assertThatThrownBy(() -> workOrderService.changeStatus(order.getId(), WorkOrderStatus.IPTAL))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aktif tahsilat");

        paymentService.cancel(payment.getId());
        workOrderService.changeStatus(order.getId(), WorkOrderStatus.IPTAL);

        assertThat(workOrderService.findById(order.getId()).getStatus()).isEqualTo(WorkOrderStatus.IPTAL);
        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("0.00");
        assertThat(count("select count(*) from account_transactions where work_order_id = ? and type = 'ALACAK'",
                order.getId())).isEqualTo(1);
    }

    @Test
    void cancelledWorkOrderDebtIsNoLongerCollectible() {
        User patron = createUser("patron4", "Patron Kullanıcı", Role.PATRON);
        Customer customer = createCustomer("İptal Borç Testi", "0546 678 90 12");
        Vehicle vehicle = createVehicle(customer, "34EFG456");
        WorkOrder order = createCompletedOrder(customer, vehicle, "10000.00");
        Long transactionId = accountService.openDebts(customer.getId()).get(0).id();

        authenticate(patron);
        workOrderService.changeStatus(order.getId(), WorkOrderStatus.IPTAL);

        assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("0.00");
        assertThat(accountService.openDebts(customer.getId())).isEmpty();

        PaymentForm form = new PaymentForm();
        form.setCustomerId(customer.getId());
        form.setAmount(new BigDecimal("1000.00"));
        form.setPaymentDate(LocalDate.now());
        form.setMethod(PaymentMethod.NAKIT);
        AllocationForm allocation = new AllocationForm();
        allocation.setTransactionId(transactionId);
        allocation.setAmount(new BigDecimal("1000.00"));
        form.setAllocations(List.of(allocation));

        assertThatThrownBy(() -> paymentService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kapalı");
    }

    @Test
    void negativeAmountsAreRejected() {
        Customer customer = createCustomer("Negatif Test", "0542 234 56 78");
        Vehicle vehicle = createVehicle(customer, "34YZA345");
        WorkOrder order = createOpenOrder(customer, vehicle);

        PaymentForm form = new PaymentForm();
        form.setCustomerId(customer.getId());
        form.setAmount(new BigDecimal("-100.00"));
        form.setPaymentDate(LocalDate.now());
        form.setMethod(PaymentMethod.NAKIT);
        assertThatThrownBy(() -> paymentService.create(form))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> accountService.createManualDebt(customer.getId(),
                new BigDecimal("-5.00"), LocalDate.now(), "Negatif"))
                .isInstanceOf(BusinessRuleException.class);

        WorkOrderItemForm item = new WorkOrderItemForm();
        item.setType(WorkOrderItemType.PARCA);
        item.setDescription("Negatif parça");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("-1.00"));
        assertThatThrownBy(() -> workOrderService.addItem(order.getId(), item))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duplicateCustomerAndPlateArePrevented() {
        Customer customer = createCustomer("Kemal Öz", "0543 345 67 89");
        createVehicle(customer, "34ABC123");

        CustomerForm duplicate = new CustomerForm();
        duplicate.setType(CustomerType.BIREYSEL);
        duplicate.setName("Kemal Öz");
        duplicate.setPhone("0543 345 67 89");
        assertThatThrownBy(() -> customerService.create(duplicate))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kayıtlı");

        Customer other = createCustomer("Başka Müşteri", "0544 456 78 90");
        assertThatThrownBy(() -> createVehicle(other, "34 abc 123"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("plaka");
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
