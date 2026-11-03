package tr.com.servisdefteri.flow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerType;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentStatus;
import tr.com.servisdefteri.report.ReportService;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class QueryFilterIT extends BusinessTestSupport {

    @Autowired
    private ReportService reportService;

    @Test
    void allNullableSearchFiltersExecuteWithPostgres() {
        Customer customer = createCustomer("Filtre Müşteri", "0547 789 01 23");
        Vehicle vehicle = createVehicle(customer, "34HIJ789");
        WorkOrder order = createCompletedOrder(customer, vehicle, "2500.00");
        createPayment(customer, "1000.00");

        LocalDate start = LocalDate.now().minusDays(2);
        LocalDate end = LocalDate.now().plusDays(1);

        assertThat(customerService.search("Filtre", true, CustomerType.BIREYSEL,
                PageRequest.of(0, 10, Sort.by("name"))).getTotalElements()).isEqualTo(1);
        assertThat(customerService.search("filtre", null, null,
                PageRequest.of(0, 10, Sort.by("name"))).getTotalElements()).isEqualTo(1);
        assertThat(customerService.search("FİLTRE", null, null,
                PageRequest.of(0, 10, Sort.by("name"))).getTotalElements()).isEqualTo(1);
        assertThat(customerService.search("0547", null, null,
                PageRequest.of(0, 10, Sort.by("name"))).getTotalElements()).isEqualTo(1);
        assertThat(customerService.search(null, null, null,
                PageRequest.of(0, 10, Sort.by("name"))).getTotalElements()).isEqualTo(1);

        assertThat(vehicleService.search("34HIJ", true, customer.getId(),
                PageRequest.of(0, 10, Sort.by("plate"))).getTotalElements()).isEqualTo(1);
        assertThat(vehicleService.search(null, null, null,
                PageRequest.of(0, 10, Sort.by("plate"))).getTotalElements()).isEqualTo(1);

        assertThat(workOrderService.search(WorkOrderStatus.TAMAMLANDI, customer.getId(), vehicle.getId(),
                start, end, order.getOrderNo(), 0, 10).getTotalElements()).isEqualTo(1);
        assertThat(workOrderService.search(null, null, null, null, null, null, 0, 10)
                .getTotalElements()).isEqualTo(1);

        assertThat(paymentService.search(customer.getId(), PaymentMethod.NAKIT, PaymentStatus.AKTIF,
                start, end, "TH-", 0, 10).getTotalElements()).isEqualTo(1);
        assertThat(paymentService.search(null, null, null, null, null, null, 0, 10)
                .getTotalElements()).isEqualTo(1);

        assertThat(reportService.workOrderStatuses(start, end)).isNotEmpty();
        assertThat(reportService.servicePartTotals(start, end)).isNotEmpty();
        assertThat(reportService.collections(start, end)).isNotEmpty();
        assertThat(reportService.collectionsTotal(start, end)).isEqualByComparingTo("1000.00");
    }
}
