package tr.com.servisdefteri.vehicle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;
import tr.com.servisdefteri.common.PlateNormalizer;
import tr.com.servisdefteri.common.SearchNormalizer;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerRepository;
import tr.com.servisdefteri.security.CurrentUserService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleOwnershipHistoryRepository ownershipHistoryRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicleRepository,
                          VehicleOwnershipHistoryRepository ownershipHistoryRepository,
                          CustomerRepository customerRepository,
                          AuditService auditService,
                          CurrentUserService currentUserService,
                          Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.ownershipHistoryRepository = ownershipHistoryRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Vehicle findById(Long id) {
        return vehicleRepository.findDetailedById(id)
                .orElseThrow(() -> NotFoundException.of("Araç", id));
    }

    @Transactional(readOnly = true)
    public Vehicle findByPlate(String plate) {
        String normalized = PlateNormalizer.normalize(plate);
        if (normalized.isEmpty()) {
            return null;
        }
        return vehicleRepository.findByPlateNormalized(normalized).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> byCustomer(Long customerId) {
        return vehicleRepository.findByCustomerIdOrderByPlateAsc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> activeVehicles() {
        return vehicleRepository.findByActiveTrueOrderByPlateAsc();
    }

    @Transactional(readOnly = true)
    public List<Vehicle> allVehicles() {
        return vehicleRepository.findAll(org.springframework.data.domain.Sort.by("plate"));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Vehicle> search(String q, Boolean active, Long customerId,
                                                                org.springframework.data.domain.Pageable pageable) {
        return vehicleRepository.search(SearchNormalizer.contains(q), active, customerId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listForExport(String q, Boolean active, Long customerId) {
        return search(q, active, customerId,
                org.springframework.data.domain.Pageable.unpaged(
                        org.springframework.data.domain.Sort.by("plate"))).getContent();
    }

    @Transactional(readOnly = true)
    public List<VehicleOwnershipHistory> ownershipHistory(Long vehicleId) {
        return ownershipHistoryRepository.findByVehicleIdOrderByChangedAtDesc(vehicleId);
    }

    @Transactional
    public Vehicle create(VehicleForm form) {
        Customer customer = customerRepository.findById(form.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", form.getCustomerId()));
        if (!customer.isActive()) {
            throw new BusinessRuleException("Pasif bir müşteriye yeni araç eklenemez.");
        }
        String plateNormalized = PlateNormalizer.normalize(form.getPlate());
        if (vehicleRepository.existsByPlateNormalized(plateNormalized)) {
            throw new BusinessRuleException("Bu plaka ile kayıtlı bir araç zaten var.");
        }
        String vin = normalizeVin(form.getVin());
        if (vin != null && vehicleRepository.existsByVin(vin)) {
            throw new BusinessRuleException("Bu şasi numarası ile kayıtlı bir araç zaten var.");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(customer);
        apply(vehicle, form, customer, vin);
        vehicle.setCreatedAt(LocalDateTime.now(clock));
        vehicle.setCreatedBy(currentUserService.idOrNull());
        Vehicle saved = vehicleRepository.save(vehicle);
        auditService.record(AuditAction.VEHICLE_CREATED, "Araç", saved.getId(),
                "Araç oluşturuldu: " + saved.getPlate() + ", müşteri=" + customer.getName());
        return saved;
    }

    @Transactional
    public Vehicle update(Long id, VehicleForm form, boolean canChangeActive) {
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("Araç", id));
        Customer newCustomer = customerRepository.findById(form.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", form.getCustomerId()));
        if (!newCustomer.isActive() && !newCustomer.getId().equals(vehicle.getCustomer().getId())) {
            throw new BusinessRuleException("Pasif bir müşteriye araç devredilemez.");
        }
        String plateNormalized = PlateNormalizer.normalize(form.getPlate());
        if (vehicleRepository.existsByPlateNormalizedAndIdNot(plateNormalized, id)) {
            throw new BusinessRuleException("Bu plaka ile kayıtlı başka bir araç var.");
        }
        String vin = normalizeVin(form.getVin());
        if (vin != null && vehicleRepository.existsByVinAndIdNot(vin, id)) {
            throw new BusinessRuleException("Bu şasi numarası ile kayıtlı başka bir araç var.");
        }
        if (form.getCurrentMileage() < vehicle.getCurrentMileage()) {
            throw new BusinessRuleException("Araç kilometresi geriye alınamaz. Mevcut kilometre: "
                    + vehicle.getCurrentMileage() + " km.");
        }
        if (!canChangeActive && form.isActive() != vehicle.isActive()) {
            throw new BusinessRuleException("Araç aktiflik durumunu değiştirme yetkiniz yok.");
        }

        Long previousCustomerId = vehicle.getCustomer().getId();
        boolean ownerChanged = !previousCustomerId.equals(newCustomer.getId());
        vehicle.setCustomer(newCustomer);
        apply(vehicle, form, newCustomer, vin);
        Vehicle saved = vehicleRepository.save(vehicle);

        if (ownerChanged) {
            VehicleOwnershipHistory history = new VehicleOwnershipHistory();
            history.setVehicleId(saved.getId());
            history.setPreviousCustomerId(previousCustomerId);
            history.setNewCustomerId(newCustomer.getId());
            history.setChangedBy(currentUserService.idOrNull());
            history.setChangedAt(LocalDateTime.now(clock));
            history.setNote("Araç sahibi güncellendi. Geçmiş iş emirleri eski müşteri kaydında korunur.");
            ownershipHistoryRepository.save(history);
        }

        auditService.record(AuditAction.VEHICLE_UPDATED, "Araç", saved.getId(),
                "Araç güncellendi: " + saved.getPlate() + ", aktif=" + saved.isActive());
        if (ownerChanged) {
            auditService.record(AuditAction.VEHICLE_OWNER_CHANGED, "Araç", saved.getId(),
                    "Araç sahibi değişti: " + previousCustomerId + " -> " + newCustomer.getId());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public long activeCount() {
        return vehicleRepository.countByActiveTrue();
    }

    public VehicleForm toForm(Vehicle vehicle) {
        VehicleForm form = new VehicleForm();
        form.setCustomerId(vehicle.getCustomer().getId());
        form.setPlate(vehicle.getPlate());
        form.setBrand(vehicle.getBrand());
        form.setModel(vehicle.getModel());
        form.setModelYear(vehicle.getModelYear());
        form.setVin(vehicle.getVin());
        form.setCurrentMileage(vehicle.getCurrentMileage());
        form.setNote(vehicle.getNote());
        form.setActive(vehicle.isActive());
        return form;
    }

    private void apply(Vehicle vehicle, VehicleForm form, Customer customer, String vin) {
        vehicle.setPlate(PlateNormalizer.normalize(form.getPlate()));
        vehicle.setPlateNormalized(PlateNormalizer.normalize(form.getPlate()));
        vehicle.setBrand(blankToNull(form.getBrand()));
        vehicle.setModel(blankToNull(form.getModel()));
        vehicle.setModelYear(form.getModelYear());
        vehicle.setVin(vin);
        vehicle.setCurrentMileage(form.getCurrentMileage());
        vehicle.setNote(blankToNull(form.getNote()));
        vehicle.setActive(form.isActive());
        vehicle.setCustomer(customer);
    }

    private String normalizeVin(String vin) {
        if (vin == null || vin.isBlank()) {
            return null;
        }
        return vin.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
