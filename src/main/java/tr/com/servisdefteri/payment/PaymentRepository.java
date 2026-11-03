package tr.com.servisdefteri.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query(value = """
            select nextval('payment_receipt_seq')
            """, nativeQuery = true)
    Long nextReceiptSequence();

    @Query(value = """
            select p from Payment p join fetch p.customer c
            where (cast(:customerId as long) is null or c.id = :customerId)
              and (cast(:method as string) is null or p.method = :method)
              and (cast(:status as string) is null or p.status = :status)
              and (cast(:start as date) is null or p.paymentDate >= :start)
              and (cast(:end as date) is null or p.paymentDate <= :end)
              and (cast(:q as string) is null
                   or lower(function('translate', c.name, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                   or lower(p.receiptNo) like :q)
            """,
            countQuery = """
                    select count(p) from Payment p join p.customer c
                    where (cast(:customerId as long) is null or c.id = :customerId)
                      and (cast(:method as string) is null or p.method = :method)
                      and (cast(:status as string) is null or p.status = :status)
                      and (cast(:start as date) is null or p.paymentDate >= :start)
                      and (cast(:end as date) is null or p.paymentDate <= :end)
                      and (cast(:q as string) is null
                           or lower(function('translate', c.name, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                           or lower(p.receiptNo) like :q)
                    """)
    Page<Payment> search(@Param("customerId") Long customerId,
                         @Param("method") PaymentMethod method,
                         @Param("status") PaymentStatus status,
                         @Param("start") LocalDate start,
                         @Param("end") LocalDate end,
                         @Param("q") String q,
                         Pageable pageable);

    @Query("select p from Payment p join fetch p.customer where p.id = :id")
    Optional<Payment> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status and p.paymentDate = :date
            """)
    BigDecimal sumByDate(@Param("date") LocalDate date, @Param("status") PaymentStatus status);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status and p.paymentDate between :start and :end
            """)
    BigDecimal sumBetween(@Param("start") LocalDate start,
                          @Param("end") LocalDate end,
                          @Param("status") PaymentStatus status);

    @Query("""
            select new tr.com.servisdefteri.payment.CollectionSummary(
                p.paymentDate, p.method, sum(p.amount), count(p))
            from Payment p
            where p.status = :status and p.paymentDate between :start and :end
            group by p.paymentDate, p.method
            order by p.paymentDate desc, p.method asc
            """)
    List<CollectionSummary> collectionSummary(@Param("start") LocalDate start,
                                             @Param("end") LocalDate end,
                                             @Param("status") PaymentStatus status);

    List<Payment> findTop8ByStatusOrderByCreatedAtDesc(PaymentStatus status);

    long countByStatus(PaymentStatus status);

    @Query("""
            select p from Payment p join fetch p.customer
            where p.customer.id = :customerId
            order by p.paymentDate desc, p.id desc
            """)
    List<Payment> findByCustomer(@Param("customerId") Long customerId);
}
