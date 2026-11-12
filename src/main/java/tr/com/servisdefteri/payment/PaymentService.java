package tr.com.servisdefteri.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.AccountTransaction;
import tr.com.servisdefteri.account.OpenDebt;
import tr.com.servisdefteri.account.TransactionType;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.SearchNormalizer;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerRepository;
import tr.com.servisdefteri.security.CurrentUserService;
import tr.com.servisdefteri.workorder.WorkOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final CustomerRepository customerRepository;
    private final AccountService accountService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAllocationRepository allocationRepository,
                          CustomerRepository customerRepository,
                          AccountService accountService,
                          AuditService auditService,
                          CurrentUserService currentUserService,
                          Clock clock) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.customerRepository = customerRepository;
        this.accountService = accountService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Payment findById(Long id) {
        return paymentRepository.findDetailedById(id)
                .orElseThrow(() -> NotFoundException.of("Tahsilat", id));
    }

    @Transactional(readOnly = true)
    public List<PaymentAllocation> allocations(Long paymentId) {
        return allocationRepository.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public Page<Payment> search(Long customerId, PaymentMethod method, PaymentStatus status,
                                LocalDate start, LocalDate end, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate")
                .and(Sort.by(Sort.Direction.DESC, "id")));
        return paymentRepository.search(customerId, method, status, start, end, like(q), pageable);
    }

    @Transactional(readOnly = true)
    public List<Payment> listForExport(Long customerId, PaymentMethod method, PaymentStatus status,
                                       LocalDate start, LocalDate end, String q) {
        return paymentRepository.search(customerId, method, status, start, end, like(q),
                org.springframework.data.domain.Pageable.unpaged(
                        Sort.by(Sort.Direction.DESC, "paymentDate")
                                .and(Sort.by(Sort.Direction.DESC, "id")))).getContent();
    }

    /**
     * İş emri tamamlanırken tek adımda tahsilat oluşturur ve borcun tamamını bu tahsilata dağıtır.
     */
    @Transactional
    public Payment collectForWorkOrder(WorkOrder workOrder, PaymentMethod method) {
        if (workOrder.getTotalAmount() == null || workOrder.getTotalAmount().signum() <= 0) {
            return null;
        }
        PaymentForm form = new PaymentForm();
        form.setCustomerId(workOrder.getCustomer().getId());
        form.setAmount(workOrder.getTotalAmount());
        form.setPaymentDate(LocalDate.now(clock));
        form.setMethod(method == null ? PaymentMethod.NAKIT : method);
        form.setDescription("İş emri " + workOrder.getOrderNo() + " tahsilatı");
        Long debtId = accountService.workOrderDebtTransactionId(workOrder.getId());
        if (debtId != null) {
            AllocationForm allocation = new AllocationForm();
            allocation.setTransactionId(debtId);
            allocation.setAmount(workOrder.getTotalAmount());
            form.getAllocations().add(allocation);
        }
        return create(form);
    }

    @Transactional
    public Payment create(PaymentForm form) {
        if (form.getAmount() == null || form.getAmount().signum() <= 0) {
            throw new BusinessRuleException("Tahsilat tutarı sıfırdan büyük olmalıdır.");
        }
        if (form.getAmount().stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Tahsilat tutarı en fazla 2 ondalık basamak içerebilir.");
        }
        if (form.getPaymentDate() == null) {
            throw new BusinessRuleException("Tahsilat tarihi zorunludur.");
        }
        if (form.getPaymentDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("Gelecek tarihli tahsilat kaydı oluşturulamaz.");
        }
        Customer customer = customerRepository.findByIdForUpdate(form.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", form.getCustomerId()));

        BigDecimal amount = money(form.getAmount());
        BigDecimal openDebtTotal = accountService.openDebtTotal(customer.getId());
        if (amount.compareTo(openDebtTotal) > 0) {
            throw new BusinessRuleException("Tahsilat tutarı açık borçtan fazla olamaz. Açık borç: "
                    + openDebtTotal.setScale(2, RoundingMode.HALF_UP) + " TL");
        }
        Payment payment = new Payment();
        payment.setCustomer(customer);
        payment.setReceiptNo(nextReceiptNo(form.getPaymentDate()));
        payment.setAmount(amount);
        payment.setPaymentDate(form.getPaymentDate());
        payment.setMethod(form.getMethod());
        payment.setStatus(PaymentStatus.AKTIF);
        payment.setDescription(blankToNull(form.getDescription()));
        payment.setCreatedBy(currentUserService.idOrNull());
        payment.setCreatedAt(LocalDateTime.now(clock));
        Payment saved = paymentRepository.save(payment);

        accountService.recordPaymentCredit(saved);
        saveAllocations(saved, form.getAllocations());

        auditService.record(AuditAction.PAYMENT_CREATED, "Tahsilat", saved.getId(),
                "Tahsilat oluşturuldu: " + saved.getReceiptNo() + ", müşteri=" + customer.getName()
                        + ", tutar=" + saved.getAmount() + ", yöntem=" + saved.getMethod());
        return saved;
    }

    @Transactional
    public Payment cancel(Long paymentId) {
        if (!currentUserService.canManageFinances()) {
            throw new BusinessRuleException("Tahsilat iptali için işletme sahibi veya muhasebe yetkisi gerekir.");
        }
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> NotFoundException.of("Tahsilat", paymentId));
        if (payment.getStatus() == PaymentStatus.IPTAL) {
            throw new BusinessRuleException("Bu tahsilat zaten iptal edilmiş.");
        }

        for (PaymentAllocation allocation : allocationRepository.findByPaymentIdAndCancelledFalse(paymentId)) {
            allocation.setCancelled(true);
            allocationRepository.save(allocation);
        }
        payment.setStatus(PaymentStatus.IPTAL);
        payment.setCancelledAt(LocalDateTime.now(clock));
        payment.setCancelledBy(currentUserService.idOrNull());
        paymentRepository.save(payment);
        accountService.reversePaymentCredit(payment);

        auditService.record(AuditAction.PAYMENT_CANCELLED, "Tahsilat", payment.getId(),
                "Tahsilat iptal edildi: " + payment.getReceiptNo() + ", tutar=" + payment.getAmount());
        return payment;
    }

    @Transactional(readOnly = true)
    public List<OpenDebt> openDebts(Long customerId) {
        return accountService.openDebts(customerId);
    }

    @Transactional(readOnly = true)
    public BigDecimal todayTotal() {
        BigDecimal total = paymentRepository.sumByDate(LocalDate.now(clock), PaymentStatus.AKTIF);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Transactional(readOnly = true)
    public BigDecimal totalBetween(LocalDate start, LocalDate end) {
        BigDecimal total = paymentRepository.sumBetween(start, end, PaymentStatus.AKTIF);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Transactional(readOnly = true)
    public List<CollectionSummary> summaryBetween(LocalDate start, LocalDate end) {
        return paymentRepository.collectionSummary(start, end, PaymentStatus.AKTIF);
    }

    @Transactional(readOnly = true)
    public List<Payment> recentPayments() {
        return paymentRepository.findTop8ByStatusOrderByCreatedAtDesc(PaymentStatus.AKTIF);
    }

    @Transactional(readOnly = true)
    public List<Payment> byCustomer(Long customerId) {
        return paymentRepository.findByCustomer(customerId);
    }

    private void saveAllocations(Payment payment, List<AllocationForm> allocationForms) {
        if (allocationForms == null || allocationForms.isEmpty()) {
            throw new BusinessRuleException("Tahsilatın tamamı açık borçlara dağıtılmalıdır. "
                    + "Tahsilat tutarı borçlara dağıtılmadan kaydedilemez.");
        }
        Map<Long, BigDecimal> remainingByTransaction = new java.util.HashMap<>();
        for (OpenDebt debt : accountService.openDebts(payment.getCustomer().getId())) {
            remainingByTransaction.put(debt.id(), debt.getRemaining());
        }

        BigDecimal allocatedTotal = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (AllocationForm form : allocationForms) {
            if (form.getTransactionId() == null || form.getAmount() == null
                    || form.getAmount().signum() == 0) {
                continue;
            }
            if (form.getAmount().signum() < 0) {
                throw new BusinessRuleException("Dağıtım tutarı negatif olamaz.");
            }
            if (form.getAmount().stripTrailingZeros().scale() > 2) {
                throw new BusinessRuleException("Dağıtım tutarı en fazla 2 ondalık basamak içerebilir.");
            }
            if (!seen.add(form.getTransactionId())) {
                throw new BusinessRuleException("Aynı borç hareketine birden fazla dağıtım yapılamaz.");
            }
            AccountTransaction transaction = accountService.findById(form.getTransactionId());
            if (!transaction.getCustomer().getId().equals(payment.getCustomer().getId())) {
                throw new BusinessRuleException("Dağıtım yapılan borç bu müşteriye ait değil.");
            }
            if (transaction.getType() != TransactionType.BORC) {
                throw new BusinessRuleException("Yalnızca borç hareketlerine tahsilat dağıtılabilir.");
            }
            BigDecimal remaining = remainingByTransaction.get(transaction.getId());
            if (remaining == null || remaining.signum() <= 0) {
                throw new BusinessRuleException("Seçilen borç hareketi kapalı; dağıtım yapılamaz.");
            }
            BigDecimal allocationAmount = money(form.getAmount());
            if (allocationAmount.compareTo(remaining) > 0) {
                throw new BusinessRuleException("Dağıtım tutarı kalan borçtan fazla olamaz. Kalan: "
                        + remaining.setScale(2, RoundingMode.HALF_UP) + " TL");
            }
            allocatedTotal = allocatedTotal.add(allocationAmount);
            if (allocatedTotal.compareTo(payment.getAmount()) > 0) {
                throw new BusinessRuleException("Borç dağıtımları tahsilat tutarını aşamaz.");
            }
            remainingByTransaction.put(transaction.getId(), remaining.subtract(allocationAmount));

            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setTransaction(transaction);
            allocation.setAmount(allocationAmount);
            allocation.setCancelled(false);
            allocationRepository.save(allocation);
        }
        if (allocatedTotal.compareTo(payment.getAmount()) < 0) {
            throw new BusinessRuleException("Tahsilatın tamamı açık borçlara dağıtılmalıdır. Dağıtılmayan: "
                    + payment.getAmount().subtract(allocatedTotal)
                    .setScale(2, RoundingMode.HALF_UP) + " TL");
        }
    }

    private String nextReceiptNo(LocalDate date) {
        Long sequence = paymentRepository.nextReceiptSequence();
        return "TH-" + date.getYear() + "-" + String.format(Locale.ROOT, "%06d", sequence);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String like(String value) {
        return SearchNormalizer.contains(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
