package tr.com.servisdefteri.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;
import tr.com.servisdefteri.customer.Customer;
import tr.com.servisdefteri.customer.CustomerRepository;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentAllocationRepository;
import tr.com.servisdefteri.payment.PaymentStatus;
import tr.com.servisdefteri.security.CurrentUserService;
import tr.com.servisdefteri.workorder.WorkOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final AccountTransactionRepository transactionRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AccountService(AccountTransactionRepository transactionRepository,
                          PaymentAllocationRepository allocationRepository,
                          CustomerRepository customerRepository,
                          AuditService auditService,
                          CurrentUserService currentUserService,
                          Clock clock) {
        this.transactionRepository = transactionRepository;
        this.allocationRepository = allocationRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Long customerId) {
        BigDecimal balance = transactionRepository.balanceForCustomer(customerId, TransactionType.BORC);
        return balance == null ? ZERO : balance.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public LedgerStatement statement(Long customerId, LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BusinessRuleException("Başlangıç ve bitiş tarihi zorunludur.");
        }
        if (start.isAfter(end)) {
            throw new BusinessRuleException("Başlangıç tarihi bitiş tarihinden sonra olamaz.");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> NotFoundException.of("Müşteri", customerId));

        BigDecimal opening = transactionRepository.balanceBefore(customerId, start, TransactionType.BORC);
        opening = opening == null ? ZERO : opening.setScale(2, RoundingMode.HALF_UP);

        List<AccountTransaction> transactions = transactionRepository.findLedger(customerId, start, end);
        BigDecimal running = opening;
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        List<LedgerRow> rows = new ArrayList<>(transactions.size());

        for (AccountTransaction transaction : transactions) {
            BigDecimal amount = transaction.getAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal debit = ZERO;
            BigDecimal credit = ZERO;
            if (transaction.getType() == TransactionType.BORC) {
                running = running.add(amount);
                totalDebit = totalDebit.add(amount);
                debit = amount;
            } else {
                running = running.subtract(amount);
                totalCredit = totalCredit.add(amount);
                credit = amount;
            }
            String workOrderNo = transaction.getWorkOrder() == null
                    ? null : transaction.getWorkOrder().getOrderNo();
            Long workOrderId = transaction.getWorkOrder() == null
                    ? null : transaction.getWorkOrder().getId();
            rows.add(new LedgerRow(transaction.getId(), transaction.getTransactionDate(),
                    transaction.getType(), transaction.getDescription(), debit, credit,
                    running, workOrderId, workOrderNo, transaction.getReversalOfId() != null));
        }

        return new LedgerStatement(customer, start, end, opening, rows, running,
                totalDebit, totalCredit);
    }

    @Transactional(readOnly = true)
    public List<OpenDebt> openDebts(Long customerId) {
        List<AccountTransaction> debts = transactionRepository
                .findByCustomerIdAndTypeAndReversalOfIdIsNullOrderByTransactionDateAscIdAsc(
                        customerId, TransactionType.BORC);
        Map<Long, BigDecimal> paidMap = new HashMap<>();
        for (Object[] row : allocationRepository.sumActiveByCustomer(customerId, PaymentStatus.AKTIF)) {
            paidMap.put((Long) row[0], (BigDecimal) row[1]);
        }
        Map<Long, BigDecimal> reversedMap = new HashMap<>();
        for (Object[] row : transactionRepository.reversalTotalsByCustomer(customerId)) {
            reversedMap.put((Long) row[0], (BigDecimal) row[1]);
        }
        List<OpenDebt> result = new ArrayList<>();
        for (AccountTransaction debt : debts) {
            BigDecimal paid = paidMap.getOrDefault(debt.getId(), ZERO);
            BigDecimal reversed = reversedMap.getOrDefault(debt.getId(), ZERO);
            BigDecimal remaining = debt.getAmount().subtract(paid).subtract(reversed);
            if (remaining.signum() > 0) {
                result.add(new OpenDebt(debt.getId(), debt.getTransactionDate(), debt.getDescription(),
                        debt.getAmount(), paid.add(reversed)));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BigDecimal openDebtTotal(Long customerId) {
        return openDebts(customerId).stream()
                .map(OpenDebt::getRemaining)
                .reduce(ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<CustomerBalance> customerBalances() {
        List<CustomerBalance> balances = new ArrayList<>(
                transactionRepository.customerBalances(TransactionType.BORC));
        balances.sort(Comparator.comparing(CustomerBalance::balance).reversed());
        return balances;
    }

    @Transactional
    public AccountTransaction createManualDebt(Long customerId, BigDecimal amount, LocalDate date,
                                                String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Borç tutarı sıfırdan büyük olmalıdır.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Borç tutarı en fazla 2 ondalık basamak içerebilir.");
        }
        if (date == null) {
            throw new BusinessRuleException("Hareket tarihi zorunludur.");
        }
        if (date.isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("Gelecek tarihli borç kaydı oluşturulamaz.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleException("Açıklama zorunludur.");
        }
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> NotFoundException.of("Müşteri", customerId));

        AccountTransaction transaction = new AccountTransaction();
        transaction.setCustomer(customer);
        transaction.setType(TransactionType.BORC);
        transaction.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        transaction.setTransactionDate(date);
        transaction.setDescription(description.trim());
        transaction.setCreatedBy(currentUserService.idOrNull());
        transaction.setCreatedAt(LocalDateTime.now(clock));
        AccountTransaction saved = transactionRepository.save(transaction);
        auditService.record(AuditAction.DEBT_CREATED, "CariHareket", saved.getId(),
                "Manuel borç: " + customer.getName() + ", tutar=" + saved.getAmount());
        return saved;
    }

    /**
     * Is emri tamamlandiginda borc hareketini olusturur. Ayni is emri icin ikinci kez kayit acmaz.
     */
    @Transactional
    public void postWorkOrderDebt(WorkOrder workOrder) {
        if (workOrder.getTotalAmount() == null || workOrder.getTotalAmount().signum() <= 0) {
            return;
        }
        customerRepository.findByIdForUpdate(workOrder.getCustomer().getId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", workOrder.getCustomer().getId()));

        if (activeWorkOrderDebt(workOrder.getId()) != null) {
            return;
        }

        LocalDate date = workOrder.getClosedAt() == null
                ? LocalDate.now(clock) : workOrder.getClosedAt().toLocalDate();
        AccountTransaction transaction = new AccountTransaction();
        transaction.setCustomer(workOrder.getCustomer());
        transaction.setType(TransactionType.BORC);
        transaction.setAmount(workOrder.getTotalAmount().setScale(2, RoundingMode.HALF_UP));
        transaction.setTransactionDate(date);
        transaction.setDescription("İş emri " + workOrder.getOrderNo() + " servis bedeli");
        transaction.setWorkOrder(workOrder);
        transaction.setCreatedBy(currentUserService.idOrNull());
        transaction.setCreatedAt(LocalDateTime.now(clock));
        AccountTransaction saved = transactionRepository.save(transaction);
        auditService.record(AuditAction.WORK_ORDER_DEBITED, "İşEmri", workOrder.getId(),
                "İş emri borçlandırıldı: " + workOrder.getOrderNo() + ", tutar=" + saved.getAmount());
    }

    @Transactional(readOnly = true)
    public Long workOrderDebtTransactionId(Long workOrderId) {
        AccountTransaction debt = activeWorkOrderDebt(workOrderId);
        return debt == null ? null : debt.getId();
    }

    /**
     * İş emrinin aktif borcunu, tahsil edilen tutarı ve kalanı döndürür.
     * Borç yoksa null döner; tamamen ödenmiş borçta kalan 0 olur.
     */
    @Transactional(readOnly = true)
    public WorkOrderDebt workOrderDebt(Long workOrderId) {
        AccountTransaction debt = activeWorkOrderDebt(workOrderId);
        if (debt == null) {
            return null;
        }
        BigDecimal paid = allocationRepository.activeAllocatedAmount(debt.getId(), PaymentStatus.AKTIF);
        if (paid == null) {
            paid = ZERO;
        }
        BigDecimal remaining = debt.getAmount().subtract(paid).setScale(2, RoundingMode.HALF_UP);
        if (remaining.signum() < 0) {
            remaining = ZERO;
        }
        return new WorkOrderDebt(debt.getId(), debt.getAmount().setScale(2, RoundingMode.HALF_UP),
                paid.setScale(2, RoundingMode.HALF_UP), remaining);
    }

    private AccountTransaction activeWorkOrderDebt(Long workOrderId) {
        List<AccountTransaction> debts = transactionRepository
                .findActiveWorkOrderDebts(workOrderId, TransactionType.BORC);
        return debts.isEmpty() ? null : debts.get(0);
    }

    /**
     * Iptal edilen is emrinin borcunu silmez; denetlenebilir ters kayit olusturur.
     */
    @Transactional
    public void reverseWorkOrderDebt(WorkOrder workOrder) {
        reverseWorkOrderDebt(workOrder, "İş emri iptali ters kaydı: ");
    }

    @Transactional
    public void reverseWorkOrderDebt(WorkOrder workOrder, String descriptionPrefix) {
        AccountTransaction original = activeWorkOrderDebt(workOrder.getId());
        if (original == null) {
            return;
        }
        BigDecimal allocated = allocationRepository.activeAllocatedAmount(original.getId(), PaymentStatus.AKTIF);
        if (allocated != null && allocated.signum() > 0) {
            throw new BusinessRuleException("Bu iş emrine bağlı aktif tahsilat var. "
                    + "İş emrini iptal etmeden önce ilgili tahsilatı iptal edin.");
        }

        customerRepository.findByIdForUpdate(workOrder.getCustomer().getId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", workOrder.getCustomer().getId()));

        AccountTransaction reversal = new AccountTransaction();
        reversal.setCustomer(original.getCustomer());
        reversal.setType(TransactionType.ALACAK);
        reversal.setAmount(original.getAmount());
        reversal.setTransactionDate(LocalDate.now(clock));
        reversal.setDescription(descriptionPrefix + workOrder.getOrderNo());
        reversal.setWorkOrder(workOrder);
        reversal.setReversalOfId(original.getId());
        reversal.setCreatedBy(currentUserService.idOrNull());
        reversal.setCreatedAt(LocalDateTime.now(clock));
        AccountTransaction saved = transactionRepository.save(reversal);
        original.setActiveDebit(false);
        transactionRepository.save(original);
        auditService.record(AuditAction.WORK_ORDER_DEBT_REVERSED, "İşEmri", workOrder.getId(),
                "İş emri borcu ters kayıtla kapatıldı: " + workOrder.getOrderNo()
                        + ", tutar=" + saved.getAmount());
    }

    @Transactional
    public void recordPaymentCredit(Payment payment) {
        customerRepository.findByIdForUpdate(payment.getCustomer().getId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", payment.getCustomer().getId()));
        if (transactionRepository.findFirstByPaymentIdAndReversalOfIdIsNull(payment.getId()).isPresent()) {
            return;
        }
        AccountTransaction transaction = new AccountTransaction();
        transaction.setCustomer(payment.getCustomer());
        transaction.setType(TransactionType.ALACAK);
        transaction.setAmount(payment.getAmount().setScale(2, RoundingMode.HALF_UP));
        transaction.setTransactionDate(payment.getPaymentDate());
        transaction.setDescription("Tahsilat " + payment.getReceiptNo());
        transaction.setPayment(payment);
        transaction.setCreatedBy(currentUserService.idOrNull());
        transaction.setCreatedAt(LocalDateTime.now(clock));
        transactionRepository.save(transaction);
    }

    /**
     * Tahsilat iptalinde alacak kaydi silinmez; ters borc kaydi olusturulur.
     */
    @Transactional
    public void reversePaymentCredit(Payment payment) {
        AccountTransaction original = transactionRepository
                .findFirstByPaymentIdAndReversalOfIdIsNull(payment.getId())
                .orElse(null);
        if (original == null) {
            return;
        }
        if (transactionRepository.findFirstByReversalOfId(original.getId()).isPresent()) {
            return;
        }
        customerRepository.findByIdForUpdate(payment.getCustomer().getId())
                .orElseThrow(() -> NotFoundException.of("Müşteri", payment.getCustomer().getId()));

        AccountTransaction reversal = new AccountTransaction();
        reversal.setCustomer(original.getCustomer());
        reversal.setType(TransactionType.BORC);
        reversal.setAmount(original.getAmount());
        reversal.setTransactionDate(LocalDate.now(clock));
        reversal.setDescription("Tahsilat iptali ters kaydı: " + payment.getReceiptNo());
        reversal.setReversalOfId(original.getId());
        reversal.setCreatedBy(currentUserService.idOrNull());
        reversal.setCreatedAt(LocalDateTime.now(clock));
        transactionRepository.save(reversal);
    }

    @Transactional(readOnly = true)
    public AccountTransaction findById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Cari hareket", id));
    }
}
