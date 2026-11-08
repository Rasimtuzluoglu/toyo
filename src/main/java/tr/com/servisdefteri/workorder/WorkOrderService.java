package tr.com.servisdefteri.workorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.SearchNormalizer;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerRepository;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentMethod;
import tr.com.servisdefteri.payment.PaymentService;
import tr.com.servisdefteri.security.CurrentUserService;
import tr.com.servisdefteri.vehicle.Vehicle;
import tr.com.servisdefteri.vehicle.VehicleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class WorkOrderService {

    private static final List<WorkOrderStatus> OPEN_STATUSES =
            List.of(WorkOrderStatus.BEKLIYOR, WorkOrderStatus.ISLEMDE);
    private static final List<WorkOrderStatus> FINISHED_STATUSES =
            List.of(WorkOrderStatus.TAMAMLANDI, WorkOrderStatus.TESLIM_EDILDI);
    private static final int UNDO_WINDOW_MINUTES = 10;

    private final WorkOrderRepository workOrderRepository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final AccountService accountService;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            CustomerRepository customerRepository,
                            VehicleRepository vehicleRepository,
                            AccountService accountService,
                            PaymentService paymentService,
                            AuditService auditService,
                            CurrentUserService currentUserService,
                            Clock clock) {
        this.workOrderRepository = workOrderRepository;
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.accountService = accountService;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkOrder findById(Long id) {
        return workOrderRepository.findDetailedById(id)
                .orElseThrow(() -> NotFoundException.of("İş emri", id));
    }

    @Transactional(readOnly = true)
    public Page<WorkOrder> search(WorkOrderStatus status, Long customerId, Long vehicleId,
                                  LocalDate start, LocalDate end, String q, int page, int size) {
        LocalDateTime from = start == null ? null : start.atStartOfDay();
        LocalDateTime to = end == null ? null : end.plusDays(1).atStartOfDay();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt")
                .and(Sort.by(Sort.Direction.DESC, "id")));
        return workOrderRepository.search(status, customerId, vehicleId, from, to, like(q), pageable);
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> listForExport(WorkOrderStatus status, Long customerId, Long vehicleId,
                                         LocalDate start, LocalDate end, String q) {
        LocalDateTime from = start == null ? null : start.atStartOfDay();
        LocalDateTime to = end == null ? null : end.plusDays(1).atStartOfDay();
        return workOrderRepository.search(status, customerId, vehicleId, from, to, like(q),
                org.springframework.data.domain.Pageable.unpaged(
                        Sort.by(Sort.Direction.DESC, "openedAt")
                                .and(Sort.by(Sort.Direction.DESC, "id")))).getContent();
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> recentByCustomer(Long customerId, int limit) {
        return workOrderRepository.findRecentByCustomer(customerId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "openedAt")));
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> recentByVehicle(Long vehicleId, int limit) {
        return workOrderRepository.findRecentByVehicle(vehicleId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "openedAt")));
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, WorkOrder> latestFinishedByVehicle() {
        return workOrderRepository.findLatestFinishedByVehicle(FINISHED_STATUSES).stream()
                .collect(java.util.stream.Collectors.toMap(
                        order -> order.getVehicle().getId(),
                        order -> order,
                        (first, second) -> first));
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> openWorkOrders(int limit) {
        return workOrderRepository.findOpen(OPEN_STATUSES, PageRequest.of(0, limit));
    }

    /**
     * Günlük iş panosu: bitmemiş işler her gün yeniden listelenir.
     */
    @Transactional(readOnly = true)
    public List<WorkOrderBoardRow> boardOpenRows() {
        LocalDate today = LocalDate.now(clock);
        return workOrderRepository.findOpen(OPEN_STATUSES, Pageable.unpaged()).stream()
                .map(order -> new WorkOrderBoardRow(order,
                        ChronoUnit.DAYS.between(order.getOpenedAt().toLocalDate(), today)))
                .toList();
    }

    /**
     * Bugün tamamlanan veya teslim edilen işler yalnızca bugün panoda görünür.
     */
    @Transactional(readOnly = true)
    public List<WorkOrder> finishedToday() {
        LocalDate today = LocalDate.now(clock);
        return workOrderRepository.findFinishedBetween(FINISHED_STATUSES,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> readyForDelivery(int limit) {
        return workOrderRepository.findOpen(List.of(WorkOrderStatus.TAMAMLANDI),
                PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<WorkOrderStatusSummary> statusSummary() {
        return workOrderRepository.statusSummary(null, null);
    }

    @Transactional
    public WorkOrder create(WorkOrderForm form) {
        Customer customer = customerRepository.findById(form.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", form.getCustomerId()));
        if (!customer.isActive()) {
            throw new BusinessRuleException("Pasif müşteri için yeni iş emri açılamaz.");
        }
        Vehicle vehicle = vehicleRepository.findById(form.getVehicleId())
                .orElseThrow(() -> NotFoundException.of("Araç", form.getVehicleId()));
        if (!vehicle.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessRuleException("Seçilen araç bu müşteriye ait değil.");
        }
        if (!vehicle.isActive()) {
            throw new BusinessRuleException("Pasif araç için yeni iş emri açılamaz.");
        }
        validateOpenedAt(form.getOpenedAt());

        WorkOrder order = new WorkOrder();
        String requestedOrderNo = blankToNull(form.getOrderNo());
        if (requestedOrderNo == null) {
            throw new BusinessRuleException("İş emri numarası zorunludur.");
        }
        if (workOrderRepository.existsByOrderNoIgnoreCase(requestedOrderNo)) {
            throw new BusinessRuleException("Bu iş emri numarası zaten kullanılıyor: " + requestedOrderNo);
        }
        order.setOrderNo(requestedOrderNo);
        order.setCustomer(customer);
        order.setVehicle(vehicle);
        order.setAssignedName(blankToNull(form.getAssignedName()));
        order.setOpenedAt(form.getOpenedAt());
        order.setStatus(WorkOrderStatus.BEKLIYOR);
        order.setCreatedAt(LocalDateTime.now(clock));
        order.setCreatedBy(currentUserService.idOrNull());
        applyDescriptiveFields(order, form);
        syncVehicleMileage(vehicle, form.getMileage());
        WorkOrder saved = workOrderRepository.save(order);
        auditService.record(AuditAction.WORK_ORDER_CREATED, "İşEmri", saved.getId(),
                "İş emri oluşturuldu: " + saved.getOrderNo() + ", müşteri=" + customer.getName()
                        + ", araç=" + vehicle.getPlate());
        return saved;
    }

    @Transactional
    public WorkOrder update(Long id, WorkOrderForm form) {
        WorkOrder order = workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("İş emri", id));
        if (order.getStatus() == WorkOrderStatus.IPTAL) {
            throw new BusinessRuleException("İptal edilmiş iş emri düzenlenemez.");
        }
        Customer customer = customerRepository.findById(form.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", form.getCustomerId()));
        Vehicle vehicle = vehicleRepository.findById(form.getVehicleId())
                .orElseThrow(() -> NotFoundException.of("Araç", form.getVehicleId()));
        if (!vehicle.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessRuleException("Seçilen araç bu müşteriye ait değil.");
        }
        if (order.isDebited() && (!order.getCustomer().getId().equals(customer.getId())
                || !order.getVehicle().getId().equals(vehicle.getId()))) {
            throw new BusinessRuleException("Borçlandırılmış iş emrinin müşterisi veya aracı değiştirilemez.");
        }
        if (order.getMileage() != null && form.getMileage() != null
                && form.getMileage() < order.getMileage()) {
            throw new BusinessRuleException("İş emri kilometresi geriye alınamaz.");
        }
        validateOpenedAt(form.getOpenedAt());

        String requestedOrderNo = blankToNull(form.getOrderNo());
        if (requestedOrderNo != null && !requestedOrderNo.equalsIgnoreCase(order.getOrderNo())) {
            if (order.isDebited()) {
                throw new BusinessRuleException("Borçlandırılmış iş emrinin numarası değiştirilemez.");
            }
            if (workOrderRepository.existsByOrderNoIgnoreCaseAndIdNot(requestedOrderNo, id)) {
                throw new BusinessRuleException("Bu iş emri numarası zaten kullanılıyor: " + requestedOrderNo);
            }
            order.setOrderNo(requestedOrderNo);
        }

        order.setCustomer(customer);
        order.setVehicle(vehicle);
        order.setAssignedName(blankToNull(form.getAssignedName()));
        order.setOpenedAt(form.getOpenedAt());
        applyDescriptiveFields(order, form);
        syncVehicleMileage(vehicle, form.getMileage());
        WorkOrder saved = workOrderRepository.save(order);
        auditService.record(AuditAction.WORK_ORDER_UPDATED, "İşEmri", saved.getId(),
                "İş emri güncellendi: " + saved.getOrderNo());
        return saved;
    }

    @Transactional
    public WorkOrder addItem(Long orderId, WorkOrderItemForm form) {
        WorkOrder order = lockEditable(orderId);
        WorkOrderItem item = new WorkOrderItem();
        applyItem(item, form);
        order.addItem(item);
        recalculate(order);
        workOrderRepository.save(order);
        auditService.record(AuditAction.WORK_ORDER_ITEM_ADDED, "İşEmri", order.getId(),
                "Kalem eklendi: " + item.getDescription() + ", tutar=" + item.getLineTotal());
        return order;
    }

    @Transactional
    public WorkOrder updateItem(Long orderId, Long itemId, WorkOrderItemForm form) {
        WorkOrder order = lockEditable(orderId);
        WorkOrderItem item = findItem(order, itemId);
        applyItem(item, form);
        recalculate(order);
        workOrderRepository.save(order);
        auditService.record(AuditAction.WORK_ORDER_ITEM_UPDATED, "İşEmri", order.getId(),
                "Kalem güncellendi: " + item.getDescription() + ", tutar=" + item.getLineTotal());
        return order;
    }

    @Transactional
    public WorkOrder removeItem(Long orderId, Long itemId) {
        WorkOrder order = lockEditable(orderId);
        WorkOrderItem item = findItem(order, itemId);
        order.removeItem(item);
        recalculate(order);
        workOrderRepository.save(order);
        auditService.record(AuditAction.WORK_ORDER_ITEM_REMOVED, "İşEmri", order.getId(),
                "Kalem kaldırıldı: " + item.getDescription());
        return order;
    }

    @Transactional
    public WorkOrder changeStatus(Long orderId, WorkOrderStatus target) {
        return changeStatus(orderId, target, true);
    }

    /**
     * Teslim tutanağı kaydedildiğinde iş emrini teslim edildi durumuna geçirir.
     * Yetki denetimi controller katmanında yapılır; bu yöntem belge kaydıyla aynı
     * transaction içinde çağrılır.
     */
    @Transactional
    public WorkOrder markDeliveredFromDocument(Long orderId) {
        return changeStatus(orderId, WorkOrderStatus.TESLIM_EDILDI, false);
    }

    private WorkOrder changeStatus(Long orderId, WorkOrderStatus target, boolean enforcePermissions) {
        WorkOrder order = workOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> NotFoundException.of("İş emri", orderId));
        WorkOrderStatus previous = order.getStatus();
        if (!previous.canTransitionTo(target)) {
            throw new BusinessRuleException("'" + previous.getLabel() + "' durumundan '"
                    + (target == null ? "" : target.getLabel()) + "' durumuna geçilemez.");
        }
        if (enforcePermissions && target == WorkOrderStatus.IPTAL
                && !currentUserService.canManageFinances()) {
            throw new BusinessRuleException("İş emri iptali için işletme sahibi veya muhasebe yetkisi gerekir.");
        }
        if (enforcePermissions && target != WorkOrderStatus.IPTAL
                && !currentUserService.canOperateWorkOrders()) {
            throw new BusinessRuleException(
                    "İş emri durumunu yalnızca işletme sahibi veya servis kullanıcısı değiştirebilir.");
        }

        if (target == WorkOrderStatus.TAMAMLANDI) {
            recalculate(order);
            order.setClosedAt(LocalDateTime.now(clock));
            if (order.getTotalAmount().signum() > 0) {
                accountService.postWorkOrderDebt(order);
            }
            order.setDebited(true);
        } else if (target == WorkOrderStatus.TESLIM_EDILDI) {
            if (order.getTotalAmount().signum() > 0 && !order.isDebited()) {
                throw new BusinessRuleException("Borçlandırılmamış iş emri teslim edilemez.");
            }
            order.setDeliveredAt(LocalDateTime.now(clock));
        } else if (target == WorkOrderStatus.IPTAL) {
            if (order.isDebited() && order.getTotalAmount().signum() > 0) {
                accountService.reverseWorkOrderDebt(order);
            }
            if (order.getClosedAt() == null) {
                order.setClosedAt(LocalDateTime.now(clock));
            }
        }

        order.setStatus(target);
        WorkOrder saved = workOrderRepository.save(order);
        auditService.record(AuditAction.WORK_ORDER_STATUS_CHANGED, "İşEmri", saved.getId(),
                "Durum değişti: " + previous + " -> " + target);
        return saved;
    }

    /**
     * İş emrini tamamlar ve aynı işlemde tahsilatı kaydeder.
     * Tahsilat başarısız olursa durum ve borç kaydı da geri alınır.
     */
    @Transactional
    public Payment completeWithPayment(Long orderId, PaymentMethod method) {
        if (!currentUserService.canManageFinances()) {
            throw new BusinessRuleException("Tahsilat için işletme sahibi veya muhasebe yetkisi gerekir.");
        }
        WorkOrder order = changeStatus(orderId, WorkOrderStatus.TAMAMLANDI);
        return paymentService.collectForWorkOrder(order, method);
    }

    /**
     * Son 10 dakika içinde tamamlanan iş emrini işlemde durumuna geri alır.
     * Borç varsa silinmez; ters kayıt oluşturulur.
     */
    @Transactional
    public WorkOrder undoCompletion(Long orderId) {
        WorkOrder order = workOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> NotFoundException.of("İş emri", orderId));
        if (!currentUserService.canOperateWorkOrders()) {
            throw new BusinessRuleException(
                    "Tamamlamayı geri almayı yalnızca işletme sahibi veya servis kullanıcısı yapabilir.");
        }
        if (order.getStatus() != WorkOrderStatus.TAMAMLANDI) {
            throw new BusinessRuleException("Yalnızca tamamlanmış iş emri geri alınabilir.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getClosedAt() == null
                || order.getClosedAt().isBefore(now.minusMinutes(UNDO_WINDOW_MINUTES))) {
            throw new BusinessRuleException("Tamamlama geri alma süresi ("
                    + UNDO_WINDOW_MINUTES + " dakika) doldu.");
        }
        if (order.isDebited() && order.getTotalAmount() != null
                && order.getTotalAmount().signum() > 0) {
            accountService.reverseWorkOrderDebt(order, "Tamamlama geri alındı: ");
        }
        order.setDebited(false);
        order.setClosedAt(null);
        order.setStatus(WorkOrderStatus.ISLEMDE);
        WorkOrder saved = workOrderRepository.save(order);
        auditService.record(AuditAction.WORK_ORDER_STATUS_CHANGED, "İşEmri", saved.getId(),
                "Tamamlama geri alındı: TAMAMLANDI -> ISLEMDE");
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean isOrderNoAvailable(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return false;
        }
        return !workOrderRepository.existsByOrderNoIgnoreCase(orderNo.trim());
    }

    @Transactional(readOnly = true)
    public List<String> recentAssignedNames() {
        return workOrderRepository.findRecentAssignedNames();
    }

    @Transactional(readOnly = true)
    public WorkOrderItemForm toItemForm(WorkOrderItem item) {
        WorkOrderItemForm form = new WorkOrderItemForm();
        form.setType(item.getType());
        form.setDescription(item.getDescription());
        form.setQuantity(item.getQuantity());
        form.setUnitPrice(item.getUnitPrice());
        return form;
    }

    @Transactional(readOnly = true)
    public WorkOrderForm toForm(WorkOrder order) {
        WorkOrderForm form = new WorkOrderForm();
        form.setOrderNo(order.getOrderNo());
        form.setCustomerId(order.getCustomer().getId());
        form.setVehicleId(order.getVehicle().getId());
        form.setAssignedName(order.getAssigneeDisplay());
        form.setOpenedAt(order.getOpenedAt());
        form.setComplaint(order.getComplaint());
        form.setDiagnosis(order.getDiagnosis());
        form.setWorkDone(order.getWorkDone());
        form.setMileage(order.getMileage());
        form.setNotes(order.getNotes());
        return form;
    }

    @Transactional(readOnly = true)
    public LocalDateTime lastServiceDate(Long vehicleId) {
        return workOrderRepository.lastServiceDate(vehicleId, FINISHED_STATUSES);
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return workOrderRepository.countByStatusIn(OPEN_STATUSES);
    }

    @Transactional(readOnly = true)
    public long inProgressCount() {
        return workOrderRepository.countByStatus(WorkOrderStatus.ISLEMDE);
    }

    private WorkOrder lockEditable(Long orderId) {
        WorkOrder order = workOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> NotFoundException.of("İş emri", orderId));
        if (order.isDebited() || order.getStatus() == WorkOrderStatus.TAMAMLANDI
                || order.getStatus() == WorkOrderStatus.TESLIM_EDILDI
                || order.getStatus() == WorkOrderStatus.IPTAL) {
            throw new BusinessRuleException("Bu iş emrinde kalem değişikliği yapılamaz. "
                    + "Yalnızca açık iş emirlerinde kalem eklenip düzenlenebilir.");
        }
        return order;
    }

    private WorkOrderItem findItem(WorkOrder order, Long itemId) {
        return order.getItems().stream()
                .filter(item -> item.getId() != null && item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("İş emri kalemi", itemId));
    }

    private void recalculate(WorkOrder order) {
        WorkOrderCalculator.Totals totals = WorkOrderCalculator.totals(order.getItems());
        order.setLaborTotal(totals.labor());
        order.setPartsTotal(totals.parts());
        order.setTotalAmount(totals.total());
    }

    private void applyItem(WorkOrderItem item, WorkOrderItemForm form) {
        if (form.getType() == null) {
            throw new BusinessRuleException("Kalem tipi seçilmelidir.");
        }
        if (form.getDescription() == null || form.getDescription().isBlank()) {
            throw new BusinessRuleException("Kalem açıklaması zorunludur.");
        }
        if (form.getQuantity() == null || form.getQuantity().signum() <= 0) {
            throw new BusinessRuleException("Kalem miktarı sıfırdan büyük olmalıdır.");
        }
        if (form.getQuantity().stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Kalem miktarı en fazla 2 ondalık basamak içerebilir.");
        }
        if (form.getUnitPrice() == null || form.getUnitPrice().signum() < 0) {
            throw new BusinessRuleException("Birim fiyat negatif olamaz.");
        }
        if (form.getUnitPrice().stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Birim fiyat en fazla 2 ondalık basamak içerebilir.");
        }
        item.setType(form.getType());
        item.setDescription(form.getDescription().trim());
        item.setQuantity(form.getQuantity().setScale(2, RoundingMode.HALF_UP));
        item.setUnitPrice(form.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
        item.setLineTotal(WorkOrderCalculator.lineTotal(form.getQuantity(), form.getUnitPrice()));
    }

    private void applyDescriptiveFields(WorkOrder order, WorkOrderForm form) {
        order.setComplaint(blankToNull(form.getComplaint()));
        order.setDiagnosis(blankToNull(form.getDiagnosis()));
        order.setWorkDone(blankToNull(form.getWorkDone()));
        order.setMileage(form.getMileage());
        order.setNotes(blankToNull(form.getNotes()));
    }

    private void syncVehicleMileage(Vehicle vehicle, Integer mileage) {
        if (mileage != null && mileage > vehicle.getCurrentMileage()) {
            vehicle.setCurrentMileage(mileage);
            vehicleRepository.save(vehicle);
        }
    }

    private void validateOpenedAt(LocalDateTime openedAt) {
        if (openedAt == null) {
            throw new BusinessRuleException("Açılış tarihi zorunludur.");
        }
        if (openedAt.isAfter(LocalDateTime.now(clock).plusMinutes(5))) {
            throw new BusinessRuleException("İş emri açılış tarihi gelecekte olamaz.");
        }
    }

    private String like(String value) {
        return SearchNormalizer.contains(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
