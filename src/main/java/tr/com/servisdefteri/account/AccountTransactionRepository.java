package tr.com.servisdefteri.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    @Query("""
            select coalesce(sum(case when t.type = :debit then t.amount else -t.amount end), 0)
            from AccountTransaction t where t.customer.id = :customerId
            """)
    BigDecimal balanceForCustomer(@Param("customerId") Long customerId,
                                  @Param("debit") TransactionType debit);

    @Query("""
            select coalesce(sum(case when t.type = :debit then t.amount else -t.amount end), 0)
            from AccountTransaction t
            where t.customer.id = :customerId and t.transactionDate < :date
            """)
    BigDecimal balanceBefore(@Param("customerId") Long customerId,
                             @Param("date") LocalDate date,
                             @Param("debit") TransactionType debit);

    @Query("""
            select t from AccountTransaction t
            left join fetch t.workOrder
            where t.customer.id = :customerId
              and t.transactionDate between :start and :end
            order by t.transactionDate asc, t.id asc
            """)
    List<AccountTransaction> findLedger(@Param("customerId") Long customerId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);

    @Query("""
            select t from AccountTransaction t
            left join fetch t.workOrder
            where t.customer.id = :customerId
            order by t.transactionDate asc, t.id asc
            """)
    List<AccountTransaction> findFullLedger(@Param("customerId") Long customerId);

    @Query("""
            select new tr.com.servisdefteri.account.CustomerBalance(
                c.id, c.name, c.phone,
                coalesce(sum(case when t.type = :debit then t.amount else -t.amount end), 0))
            from Customer c left join AccountTransaction t on t.customer = c
            group by c.id, c.name, c.phone
            order by c.name
            """)
    List<CustomerBalance> customerBalances(@Param("debit") TransactionType debit);

    List<AccountTransaction> findByCustomerIdAndTypeAndReversalOfIdIsNullOrderByTransactionDateAscIdAsc(
            Long customerId, TransactionType type);

    @Query("""
            select t.reversalOfId, coalesce(sum(t.amount), 0)
            from AccountTransaction t
            where t.customer.id = :customerId and t.reversalOfId is not null
            group by t.reversalOfId
            """)
    List<Object[]> reversalTotalsByCustomer(@Param("customerId") Long customerId);

    @Query("""
            select t from AccountTransaction t
            where t.workOrder.id = :workOrderId
              and t.type = :type
              and t.reversalOfId is null
              and t.activeDebit = true
              and not exists (
                  select r.id from AccountTransaction r where r.reversalOfId = t.id
              )
            order by t.id desc
            """)
    List<AccountTransaction> findActiveWorkOrderDebts(@Param("workOrderId") Long workOrderId,
                                                      @Param("type") TransactionType type);

    Optional<AccountTransaction> findFirstByReversalOfId(Long reversalOfId);

    Optional<AccountTransaction> findFirstByPaymentIdAndReversalOfIdIsNull(Long paymentId);

    @Query("""
            select coalesce(sum(t.amount), 0) from AccountTransaction t
            where t.type = :debit and t.customer.id = :customerId
            """)
    BigDecimal totalByTypeForCustomer(@Param("customerId") Long customerId,
                                      @Param("debit") TransactionType debit);

    @Query("""
            select t from AccountTransaction t join fetch t.customer
            order by t.createdAt desc, t.id desc
            """)
    List<AccountTransaction> findRecent(org.springframework.data.domain.Pageable pageable);

    @Query("""
            select t from AccountTransaction t join fetch t.customer
            where t.customer.id = :customerId
            order by t.createdAt desc, t.id desc
            """)
    List<AccountTransaction> findRecentByCustomer(@Param("customerId") Long customerId,
                                                  org.springframework.data.domain.Pageable pageable);
}
