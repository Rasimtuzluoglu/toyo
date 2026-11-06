package tr.com.servisdefteri.workorder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.PhoneNormalizer;
import tr.com.servisdefteri.common.PlateNormalizer;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerForm;
import tr.com.servisdefteri.customer.CustomerRepository;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.customer.CustomerType;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleForm;
import tr.com.servisdefteri.vehicle.VehicleService;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Plaka bilgisinden tek adimda musteri + arac + is emri olusturur.
 * Plaka zaten kayitliysa mevcut arac ve musteri kullanilir; yeni kayit acilmaz.
 */
@Service
public class QuickWorkOrderService {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final WorkOrderService workOrderService;
    private final Clock clock;

    public QuickWorkOrderService(CustomerRepository customerRepository, CustomerService customerService,
                                 VehicleService vehicleService, WorkOrderService workOrderService,
                                 Clock clock) {
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.workOrderService = workOrderService;
        this.clock = clock;
    }

    @Transactional
    public WorkOrder create(QuickWorkOrderForm form) {
        String plate = PlateNormalizer.normalize(form.getPlate());
        if (!PlateNormalizer.isValid(form.getPlate())) {
            throw new BusinessRuleException("Geçerli bir plaka giriniz. Örnek: 34 ABC 123");
        }

        Vehicle vehicle = vehicleService.findByPlate(plate);
        Customer customer;
        if (vehicle != null) {
            customer = vehicle.getCustomer();
        } else {
            String phoneNormalized = PhoneNormalizer.normalize(form.getPhone());
            String customerName = form.getCustomerName() == null ? "" : form.getCustomerName().trim();
            if (customerName.isBlank() || phoneNormalized.isBlank()) {
                throw new BusinessRuleException("Yeni plaka için müşteri adı ve telefon zorunludur.");
            }
            customer = customerRepository
                    .findFirstByPhoneNormalizedAndNameIgnoreCase(phoneNormalized, customerName)
                    .orElseGet(() -> {
                        CustomerForm customerForm = new CustomerForm();
                        customerForm.setType(CustomerType.BIREYSEL);
                        customerForm.setName(customerName);
                        customerForm.setPhone(form.getPhone());
                        customerForm.setActive(true);
                        return customerService.create(customerForm);
                    });

            VehicleForm vehicleForm = new VehicleForm();
            vehicleForm.setCustomerId(customer.getId());
            vehicleForm.setPlate(form.getPlate());
            vehicleForm.setBrand(blankToNull(form.getBrand()));
            vehicleForm.setModel(blankToNull(form.getModel()));
            vehicleForm.setCurrentMileage(form.getMileage() == null ? 0 : form.getMileage());
            vehicleForm.setActive(true);
            vehicle = vehicleService.create(vehicleForm);
        }

        WorkOrderForm orderForm = new WorkOrderForm();
        orderForm.setOrderNo(form.getOrderNo());
        orderForm.setCustomerId(customer.getId());
        orderForm.setVehicleId(vehicle.getId());
        orderForm.setOpenedAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
        orderForm.setMileage(form.getMileage() != null ? form.getMileage()
                : (vehicle.getCurrentMileage() > 0 ? vehicle.getCurrentMileage() : null));
        orderForm.setComplaint(blankToNull(form.getComplaint()));
        orderForm.setAssignedUserId(form.getAssignedUserId());
        orderForm.setNotes(blankToNull(form.getNotes()));
        WorkOrder order = workOrderService.create(orderForm);

        String serviceDescription = blankToNull(form.getServiceDescription());
        if (serviceDescription != null || (form.getServiceAmount() != null
                && form.getServiceAmount().signum() > 0)) {
            WorkOrderItemForm item = new WorkOrderItemForm();
            item.setType(WorkOrderItemType.HIZMET);
            item.setDescription(serviceDescription != null ? serviceDescription : "Hızlı servis işlemi");
            item.setQuantity(java.math.BigDecimal.ONE);
            item.setUnitPrice(form.getServiceAmount() == null
                    ? java.math.BigDecimal.ZERO : form.getServiceAmount());
            workOrderService.addItem(order.getId(), item);
        }

        if (form.isCompleteNow()) {
            workOrderService.changeStatus(order.getId(), WorkOrderStatus.ISLEMDE);
            workOrderService.changeStatus(order.getId(), WorkOrderStatus.TAMAMLANDI);
            order = workOrderService.findById(order.getId());
        }
        return order;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
