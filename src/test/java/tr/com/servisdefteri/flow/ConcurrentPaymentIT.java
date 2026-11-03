package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import tr.com.servisdefteri.account.OpenDebt;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.payment.AllocationForm;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentForm;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentPaymentIT extends BusinessTestSupport {

    @Test
    void concurrentPaymentsDoNotBreakBalanceOrDoubleAllocate() throws Exception {
        Customer customer = createCustomer("Eşzamanlı Test", "0545 567 89 01");
        Vehicle vehicle = createVehicle(customer, "34BCD234");
        createCompletedOrder(customer, vehicle, "10000.00");
        List<OpenDebt> debts = accountService.openDebts(customer.getId());
        Long transactionId = debts.get(0).id();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Payment> first = pool.submit(() -> concurrentPayment(customer, transactionId, ready, start));
            Future<Payment> second = pool.submit(() -> concurrentPayment(customer, transactionId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Payment p1 = first.get(30, TimeUnit.SECONDS);
            Payment p2 = second.get(30, TimeUnit.SECONDS);

            assertThat(p1.getId()).isNotEqualTo(p2.getId());
            assertThat(accountService.balance(customer.getId())).isEqualByComparingTo("8000.00");
            assertThat(accountService.openDebtTotal(customer.getId())).isEqualByComparingTo("8000.00");
            BigDecimal allocated = jdbcTemplate.queryForObject(
                    "select coalesce(sum(amount), 0) from payment_allocations where transaction_id = ? and cancelled = false",
                    BigDecimal.class, transactionId);
            assertThat(allocated).isEqualByComparingTo("2000.00");
            assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Long.class)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    private Payment concurrentPayment(Customer customer, Long transactionId,
                                      CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        PaymentForm form = new PaymentForm();
        form.setCustomerId(customer.getId());
        form.setAmount(new BigDecimal("1000.00"));
        form.setPaymentDate(LocalDate.now());
        form.setMethod(PaymentMethod.NAKIT);
        AllocationForm allocation = new AllocationForm();
        allocation.setTransactionId(transactionId);
        allocation.setAmount(new BigDecimal("1000.00"));
        form.setAllocations(List.of(allocation));
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        return paymentService.create(form);
    }
}
