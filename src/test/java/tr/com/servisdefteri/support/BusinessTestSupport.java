package tr.com.servisdefteri.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.OpenDebt;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerForm;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.customer.CustomerType;
import tr.com.servisdefteri.payment.AllocationForm;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentForm;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentService;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleForm;
import tr.com.servisdefteri.vehicle.VehicleService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderForm;
import tr.com.servisdefteri.workorder.WorkOrderItemForm;
import tr.com.servisdefteri.workorder.WorkOrderItemType;
import tr.com.servisdefteri.workorder.WorkOrderService;
import tr.com.servisdefteri.workorder.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class BusinessTestSupport extends IntegrationTestSupport {

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected VehicleService vehicleService;

    @Autowired
    protected WorkOrderService workOrderService;

    @Autowired
    protected AccountService accountService;

    @Autowired
    protected PaymentService paymentService;

    protected Customer createCustomer(String name, String phone) {
        CustomerForm form = new CustomerForm();
        form.setType(CustomerType.BIREYSEL);
        form.setName(name);
        form.setPhone(phone);
        form.setActive(true);
        return customerService.create(form);
    }

    protected Vehicle createVehicle(Customer customer, String plate) {
        VehicleForm form = new VehicleForm();
        form.setCustomerId(customer.getId());
        form.setPlate(plate);
        form.setBrand("Test");
        form.setModel("Model");
        form.setModelYear(2020);
        form.setCurrentMileage(1000);
        form.setActive(true);
        return vehicleService.create(form);
    }

    protected WorkOrder createOpenOrder(Customer customer, Vehicle vehicle) {
        WorkOrderForm form = new WorkOrderForm();
        form.setCustomerId(customer.getId());
        form.setVehicleId(vehicle.getId());
        form.setOpenedAt(LocalDateTime.now().minusHours(2));
        form.setComplaint("Test şikâyeti");
        form.setMileage(1200);
        return workOrderService.create(form);
    }

    protected WorkOrder createWorkOrderWithItem(Customer customer, Vehicle vehicle, String amount) {
        WorkOrder order = createOpenOrder(customer, vehicle);
        WorkOrderItemForm item = new WorkOrderItemForm();
        item.setType(WorkOrderItemType.HIZMET);
        item.setDescription("Test işçiliği");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal(amount));
        workOrderService.addItem(order.getId(), item);
        return workOrderService.findById(order.getId());
    }

    protected WorkOrder createCompletedOrder(Customer customer, Vehicle vehicle, String amount) {
        WorkOrder order = createWorkOrderWithItem(customer, vehicle, amount);
        runAsSystemPatron(() -> {
            workOrderService.changeStatus(order.getId(), WorkOrderStatus.ISLEMDE);
            workOrderService.changeStatus(order.getId(), WorkOrderStatus.TAMAMLANDI);
        });
        return workOrderService.findById(order.getId());
    }

    /**
     * Durum değişiklikleri oturum sahibinin rolünü denetler. Test verisi hazırlarken
     * önceki oturumu bozmadan işletme sahibi kimliğiyle işlem yapar.
     */
    protected void runAsSystemPatron(Runnable action) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        authenticate(systemPatron());
        try {
            action.run();
        } finally {
            if (previous == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previous);
            }
        }
    }

    private User systemPatron;

    private User systemPatron() {
        if (systemPatron == null) {
            systemPatron = createUser("sistem-patron", "Sistem İşlemleri", Role.PATRON);
        }
        return systemPatron;
    }

    protected Payment createPayment(Customer customer, String amount) {
        List<OpenDebt> debts = accountService.openDebts(customer.getId());
        PaymentForm form = new PaymentForm();
        form.setCustomerId(customer.getId());
        form.setAmount(new BigDecimal(amount));
        form.setPaymentDate(LocalDate.now());
        form.setMethod(PaymentMethod.NAKIT);
        form.setDescription("Test tahsilatı");
        List<AllocationForm> allocations = new ArrayList<>();
        BigDecimal remainingToAllocate = new BigDecimal(amount);
        for (OpenDebt debt : debts) {
            if (remainingToAllocate.signum() <= 0) {
                break;
            }
            BigDecimal allocation = remainingToAllocate.min(debt.getRemaining());
            AllocationForm allocationForm = new AllocationForm();
            allocationForm.setTransactionId(debt.id());
            allocationForm.setAmount(allocation);
            allocations.add(allocationForm);
            remainingToAllocate = remainingToAllocate.subtract(allocation);
        }
        form.setAllocations(allocations);
        return paymentService.create(form);
    }

    protected void authenticate(User user) {
        AppUserPrincipal principal = principal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                        principal.getAuthorities()));
    }

    protected void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
