package tr.com.servisdefteri.appointment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerRepository;
import tr.com.servisdefteri.security.CurrentUserService;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              CustomerRepository customerRepository,
                              VehicleRepository vehicleRepository,
                              AuditService auditService,
                              CurrentUserService currentUserService,
                              Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Appointment> day(LocalDate date) {
        return appointmentRepository.findDay(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<Appointment> week(LocalDate start) {
        return appointmentRepository.findBetween(start.atStartOfDay(), start.plusDays(7).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public Appointment findById(Long id) {        return appointmentRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Randevu", id));
    }

    @Transactional
    public Appointment create(AppointmentForm form) {
        if (form.getStartsAt().isBefore(LocalDateTime.now(clock).minusMinutes(5))) {
            throw new BusinessRuleException("Geçmiş tarihli randevu oluşturulamaz.");
        }
        Customer customer = customerRepository.findById(form.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", form.getCustomerId()));
        Vehicle vehicle = vehicleRepository.findById(form.getVehicleId())
                .orElseThrow(() -> NotFoundException.of("Araç", form.getVehicleId()));
        validateOwnership(customer, vehicle);
        validateConflict(vehicle.getId(), form.getStartsAt(), null);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setVehicle(vehicle);
        appointment.setStartsAt(form.getStartsAt());
        appointment.setStatus(form.getStatus() == null ? AppointmentStatus.PLANLANDI : form.getStatus());
        appointment.setNotes(blankToNull(form.getNotes()));
        appointment.setCreatedAt(LocalDateTime.now(clock));
        appointment.setCreatedBy(currentUserService.idOrNull());
        Appointment saved = appointmentRepository.save(appointment);
        auditService.record(AuditAction.APPOINTMENT_CREATED, "Randevu", saved.getId(),
                "Randevu oluşturuldu: " + customer.getName() + ", " + vehicle.getPlate());
        return saved;
    }

    @Transactional
    public Appointment update(Long id, AppointmentForm form) {
        Appointment appointment = findById(id);
        Customer customer = customerRepository.findById(form.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", form.getCustomerId()));
        Vehicle vehicle = vehicleRepository.findById(form.getVehicleId())
                .orElseThrow(() -> NotFoundException.of("Araç", form.getVehicleId()));
        validateOwnership(customer, vehicle);
        validateConflict(vehicle.getId(), form.getStartsAt(), id);

        appointment.setCustomer(customer);
        appointment.setVehicle(vehicle);
        appointment.setStartsAt(form.getStartsAt());
        appointment.setStatus(form.getStatus() == null ? AppointmentStatus.PLANLANDI : form.getStatus());
        appointment.setNotes(blankToNull(form.getNotes()));
        appointment.setUpdatedAt(LocalDateTime.now(clock));
        Appointment saved = appointmentRepository.save(appointment);
        auditService.record(AuditAction.APPOINTMENT_UPDATED, "Randevu", saved.getId(),
                "Randevu güncellendi: " + customer.getName() + ", " + vehicle.getPlate());
        return saved;
    }

    @Transactional
    public Appointment markArrived(Long id) {
        Appointment appointment = findById(id);
        appointment.setStatus(AppointmentStatus.GELDI);
        appointment.setUpdatedAt(LocalDateTime.now(clock));
        Appointment saved = appointmentRepository.save(appointment);
        auditService.record(AuditAction.APPOINTMENT_UPDATED, "Randevu", saved.getId(),
                "Randevu geldi olarak işaretlendi.");
        return saved;
    }

    @Transactional(readOnly = true)
    public String vehiclePlate(Long appointmentId) {
        return findById(appointmentId).getVehicle().getPlate();
    }

    @Transactional
    public Appointment cancel(Long id) {
        Appointment appointment = findById(id);
        appointment.setStatus(AppointmentStatus.IPTAL);
        appointment.setUpdatedAt(LocalDateTime.now(clock));
        Appointment saved = appointmentRepository.save(appointment);
        auditService.record(AuditAction.APPOINTMENT_CANCELLED, "Randevu", saved.getId(),
                "Randevu iptal edildi.");
        return saved;
    }

    public AppointmentForm toForm(Appointment appointment) {
        AppointmentForm form = new AppointmentForm();
        form.setCustomerId(appointment.getCustomer().getId());
        form.setVehicleId(appointment.getVehicle().getId());
        form.setStartsAt(appointment.getStartsAt());
        form.setStatus(appointment.getStatus());
        form.setNotes(appointment.getNotes());
        return form;
    }

    private void validateOwnership(Customer customer, Vehicle vehicle) {
        if (!vehicle.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessRuleException("Seçilen araç bu müşteriye ait değil.");
        }
        if (!customer.isActive() || !vehicle.isActive()) {
            throw new BusinessRuleException("Pasif müşteri veya araç için randevu oluşturulamaz.");
        }
    }

    private void validateConflict(Long vehicleId, LocalDateTime startsAt, Long excludeId) {
        if (appointmentRepository.countConflict(vehicleId, startsAt, AppointmentStatus.IPTAL, excludeId) > 0) {
            throw new BusinessRuleException("Bu araç için aynı saatte zaten bir randevu var.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
