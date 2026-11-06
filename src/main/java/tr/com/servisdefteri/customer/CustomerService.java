package tr.com.servisdefteri.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;
import tr.com.servisdefteri.common.PhoneNormalizer;
import tr.com.servisdefteri.common.SearchNormalizer;
import tr.com.servisdefteri.security.CurrentUserService;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public CustomerService(CustomerRepository customerRepository, VehicleRepository vehicleRepository,
                           AuditService auditService,
                           CurrentUserService currentUserService, Clock clock) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Müşteri", id));
    }

    @Transactional(readOnly = true)
    public List<Customer> activeCustomers() {
        return customerRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Customer> allCustomers() {
        return customerRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Customer> listForExport(String q, Boolean active, CustomerType type) {
        return search(q, active, type,
                org.springframework.data.domain.Pageable.unpaged(
                        org.springframework.data.domain.Sort.by("name").ascending())).getContent();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Customer> search(String q, Boolean active, CustomerType type,
                                                                  org.springframework.data.domain.Pageable pageable) {
        String query = SearchNormalizer.contains(q);
        String digits = PhoneNormalizer.normalizeForSearch(q);
        String phoneLike = digits.length() >= 2 ? "%" + digits + "%" : "NO_PHONE_MATCH";
        return customerRepository.search(query, phoneLike, active, type, pageable);
    }

    @Transactional
    public Customer create(CustomerForm form) {
        String phoneNormalized = PhoneNormalizer.normalize(form.getPhone());
        String name = form.getName().trim();
        if (customerRepository.existsByPhoneNormalizedAndNameIgnoreCase(phoneNormalized, name)) {
            throw new BusinessRuleException("Bu telefon ve isimle kayıtlı bir müşteri zaten var. "
                    + "Lütfen mevcut müşteri kaydını kontrol edin.");
        }
        Customer customer = new Customer();
        apply(customer, form);
        customer.setCreatedAt(LocalDateTime.now(clock));
        customer.setCreatedBy(currentUserService.idOrNull());
        Customer saved = customerRepository.save(customer);
        auditService.record(AuditAction.CUSTOMER_CREATED, "Müşteri", saved.getId(),
                "Müşteri oluşturuldu: " + saved.getName());
        return saved;
    }

    @Transactional
    public Customer update(Long id, CustomerForm form, boolean canChangeActive) {
        Customer customer = findById(id);
        String phoneNormalized = PhoneNormalizer.normalize(form.getPhone());
        String name = form.getName().trim();
        if (customerRepository.existsByPhoneNormalizedAndNameIgnoreCaseAndIdNot(phoneNormalized, name, id)) {
            throw new BusinessRuleException("Bu telefon ve isimle kayıtlı başka bir müşteri var.");
        }
        if (!canChangeActive && form.isActive() != customer.isActive()) {
            throw new BusinessRuleException("Müşteri aktiflik durumunu değiştirme yetkiniz yok.");
        }
        apply(customer, form);
        Customer saved = customerRepository.save(customer);
        auditService.record(AuditAction.CUSTOMER_UPDATED, "Müşteri", saved.getId(),
                "Müşteri güncellendi: " + saved.getName() + ", aktif=" + saved.isActive());
        return saved;
    }

    /**
     * Finansal/geçmiş kayıtları silmeden müşteriyi ve araçlarını pasife alır.
     */
    @Transactional
    public void deactivate(Long id) {
        Customer customer = findById(id);
        if (!customer.isActive()) {
            return;
        }
        customer.setActive(false);
        customerRepository.save(customer);

        List<Vehicle> vehicles = vehicleRepository.findByCustomerIdOrderByPlateAsc(id);
        for (Vehicle vehicle : vehicles) {
            vehicle.setActive(false);
        }
        vehicleRepository.saveAll(vehicles);

        auditService.record(AuditAction.CUSTOMER_DELETED, "Müşteri", customer.getId(),
                "Müşteri silindi (pasife alındı): " + customer.getName()
                        + ", araç sayısı=" + vehicles.size());
    }

    @Transactional
    public Customer findByIdForUpdate(Long id) {
        return customerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("Müşteri", id));
    }

    @Transactional(readOnly = true)
    public long activeCount() {
        return customerRepository.countByActiveTrue();
    }

    private void apply(Customer customer, CustomerForm form) {
        customer.setType(form.getType());
        customer.setName(form.getName().trim());
        customer.setPhone(form.getPhone().trim());
        customer.setPhoneNormalized(PhoneNormalizer.normalize(form.getPhone()));
        customer.setEmail(blankToNull(form.getEmail()));
        customer.setAddress(blankToNull(form.getAddress()));
        customer.setTaxOffice(blankToNull(form.getTaxOffice()));
        customer.setTaxNumber(blankToNull(form.getTaxNumber()));
        customer.setNote(blankToNull(form.getNote()));
        customer.setActive(form.isActive());
    }

    public CustomerForm toForm(Customer customer) {
        CustomerForm form = new CustomerForm();
        form.setType(customer.getType());
        form.setName(customer.getName());
        form.setPhone(customer.getPhone());
        form.setEmail(customer.getEmail());
        form.setAddress(customer.getAddress());
        form.setTaxOffice(customer.getTaxOffice());
        form.setTaxNumber(customer.getTaxNumber());
        form.setNote(customer.getNote());
        form.setActive(customer.isActive());
        return form;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
