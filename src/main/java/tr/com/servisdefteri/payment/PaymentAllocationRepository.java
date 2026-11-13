package tr.com.servisdefteri.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    @Query("""
            select coalesce(sum(a.amount), 0) from PaymentAllocation a
            where a.transaction.id = :transactionId and a.cancelled = false and a.payment.status = :status
            """)
    BigDecimal sumActiveByTransaction(@Param("transactionId") Long transactionId,
                                      @Param("status") PaymentStatus status);

    @Query("""
            select a.transaction.id, coalesce(sum(a.amount), 0)
            from PaymentAllocation a
            where a.transaction.customer.id = :customerId
              and a.cancelled = false and a.payment.status = :status
            group by a.transaction.id
            """)
    List<Object[]> sumActiveByCustomer(@Param("customerId") Long customerId,
                                       @Param("status") PaymentStatus status);

    @Query("""
            select t.workOrder.id, coalesce(sum(a.amount), 0)
            from PaymentAllocation a join a.transaction t
            where a.cancelled = false and a.payment.status = :status
              and t.workOrder.id in :workOrderIds
            group by t.workOrder.id
            """)
    List<Object[]> sumActiveByWorkOrderIds(@Param("workOrderIds") Collection<Long> workOrderIds,
                                           @Param("status") PaymentStatus status);

    @Query("""
            select a from PaymentAllocation a join fetch a.transaction t
            where a.payment.id = :paymentId order by a.id
            """)
    List<PaymentAllocation> findByPaymentId(@Param("paymentId") Long paymentId);

    List<PaymentAllocation> findByPaymentIdAndCancelledFalse(Long paymentId);

    boolean existsByTransactionIdAndCancelledFalse(Long transactionId);

    @Query("""
            select coalesce(sum(a.amount), 0) from PaymentAllocation a
            where a.transaction.id = :transactionId and a.cancelled = false and a.payment.status = :status
            """)
    BigDecimal activeAllocatedAmount(@Param("transactionId") Long transactionId,
                                     @Param("status") PaymentStatus status);
}
