package tr.com.servisdefteri.workorder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.security.CurrentUserService;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class VehicleDocumentService {

    private final VehicleIntakeRepository intakeRepository;
    private final VehicleDeliveryRepository deliveryRepository;
    private final WorkOrderService workOrderService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public VehicleDocumentService(VehicleIntakeRepository intakeRepository,
                                  VehicleDeliveryRepository deliveryRepository,
                                  WorkOrderService workOrderService,
                                  AuditService auditService,
                                  CurrentUserService currentUserService,
                                  Clock clock) {
        this.intakeRepository = intakeRepository;
        this.deliveryRepository = deliveryRepository;
        this.workOrderService = workOrderService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VehicleIntake findIntake(Long workOrderId) {
        return intakeRepository.findByWorkOrderId(workOrderId).orElse(null);
    }

    @Transactional(readOnly = true)
    public VehicleDelivery findDelivery(Long workOrderId) {
        return deliveryRepository.findByWorkOrderId(workOrderId).orElse(null);
    }

    @Transactional
    public VehicleIntake saveIntake(Long workOrderId, VehicleIntakeForm form) {
        WorkOrder order = workOrderService.findById(workOrderId);
        VehicleIntake intake = intakeRepository.findByWorkOrderId(workOrderId).orElseGet(() -> {
            VehicleIntake created = new VehicleIntake();
            created.setWorkOrder(order);
            created.setIntakeAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
            created.setCreatedAt(LocalDateTime.now(clock));
            created.setCreatedBy(currentUserService.idOrNull());
            return created;
        });
        intake.setFuelLevel(form.getFuelLevel());
        intake.setDamageNotes(blankToNull(form.getDamageNotes()));
        intake.setValuables(blankToNull(form.getValuables()));
        intake.setSignatureData(signature(form.getSignatureData()));
        intake.setUpdatedAt(LocalDateTime.now(clock));
        VehicleIntake saved = intakeRepository.save(intake);
        auditService.record(AuditAction.VEHICLE_INTAKE_SAVED, "İşEmri", order.getId(),
                "Araç kabul formu kaydedildi: " + order.getOrderNo()
                        + ", yakıt=" + form.getFuelLevel().getLabel());
        return saved;
    }

    @Transactional
    public VehicleDelivery saveDelivery(Long workOrderId, VehicleDeliveryForm form) {
        WorkOrder order = workOrderService.findById(workOrderId);
        if (!order.getStatus().isCompleted()) {
            throw new BusinessRuleException(
                    "Teslim tutanağı için iş emri en az Tamamlandı durumunda olmalıdır.");
        }
        VehicleDelivery delivery = deliveryRepository.findByWorkOrderId(workOrderId).orElseGet(() -> {
            VehicleDelivery created = new VehicleDelivery();
            created.setWorkOrder(order);
            created.setDeliveredAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
            created.setCreatedAt(LocalDateTime.now(clock));
            created.setCreatedBy(currentUserService.idOrNull());
            return created;
        });
        delivery.setReceiverName(form.getReceiverName().trim());
        delivery.setMileage(form.getMileage() == null ? order.getMileage() : form.getMileage());
        delivery.setFuelLevel(form.getFuelLevel());
        delivery.setNotes(blankToNull(form.getNotes()));
        delivery.setSignatureData(signature(form.getSignatureData()));
        delivery.setUpdatedAt(LocalDateTime.now(clock));
        VehicleDelivery saved = deliveryRepository.save(delivery);
        auditService.record(AuditAction.VEHICLE_DELIVERY_SAVED, "İşEmri", order.getId(),
                "Teslim tutanağı kaydedildi: " + order.getOrderNo()
                        + ", teslim alan=" + delivery.getReceiverName());
        return saved;
    }

    public VehicleIntakeForm toForm(VehicleIntake intake) {
        VehicleIntakeForm form = new VehicleIntakeForm();
        if (intake != null) {
            form.setFuelLevel(intake.getFuelLevel());
            form.setDamageNotes(intake.getDamageNotes());
            form.setValuables(intake.getValuables());
            form.setSignatureData(intake.getSignatureData());
        }
        return form;
    }

    public VehicleDeliveryForm toForm(VehicleDelivery delivery, WorkOrder order) {
        VehicleDeliveryForm form = new VehicleDeliveryForm();
        if (delivery != null) {
            form.setReceiverName(delivery.getReceiverName());
            form.setMileage(delivery.getMileage());
            form.setFuelLevel(delivery.getFuelLevel());
            form.setNotes(delivery.getNotes());
            form.setSignatureData(delivery.getSignatureData());
        } else {
            form.setReceiverName(order.getCustomer().getName());
            form.setMileage(order.getMileage() != null ? order.getMileage()
                    : order.getVehicle().getCurrentMileage());
            form.setFuelLevel(FuelLevel.YARIM);
        }
        return form;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String signature(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("data:image/png;base64,")) {
            throw new BusinessRuleException("Geçersiz imza verisi.");
        }
        return trimmed;
    }
}
