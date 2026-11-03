package tr.com.servisdefteri.workorder;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    @Query(value = "select nextval('work_order_no_seq')", nativeQuery = true)
    Long nextOrderSequence();

    @Query(value = """
            select w from WorkOrder w join fetch w.customer c join fetch w.vehicle v
            left join fetch w.assignedUser
            where (cast(:status as string) is null or w.status = :status)
              and (cast(:customerId as long) is null or c.id = :customerId)
              and (cast(:vehicleId as long) is null or v.id = :vehicleId)
              and (cast(:start as timestamp) is null or w.openedAt >= :start)
              and (cast(:end as timestamp) is null or w.openedAt < :end)
              and (cast(:q as string) is null or lower(w.orderNo) like :q
                   or lower(function('translate', c.name, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                   or lower(function('translate', v.plate, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q)
            """,
            countQuery = """
                    select count(w) from WorkOrder w join w.customer c join w.vehicle v
                    where (cast(:status as string) is null or w.status = :status)
                      and (cast(:customerId as long) is null or c.id = :customerId)
                      and (cast(:vehicleId as long) is null or v.id = :vehicleId)
                      and (cast(:start as timestamp) is null or w.openedAt >= :start)
                      and (cast(:end as timestamp) is null or w.openedAt < :end)
                      and (cast(:q as string) is null or lower(w.orderNo) like :q
                           or lower(function('translate', c.name, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                           or lower(function('translate', v.plate, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q)
                    """)
    Page<WorkOrder> search(@Param("status") WorkOrderStatus status,
                           @Param("customerId") Long customerId,
                           @Param("vehicleId") Long vehicleId,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end,
                           @Param("q") String q,
                           Pageable pageable);

    @Query("""
            select distinct w from WorkOrder w
            join fetch w.customer join fetch w.vehicle
            left join fetch w.assignedUser
            left join fetch w.items
            where w.id = :id
            """)
    Optional<WorkOrder> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkOrder w where w.id = :id")
    Optional<WorkOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select w from WorkOrder w join fetch w.vehicle
            left join fetch w.assignedUser
            where w.customer.id = :customerId
            """)
    List<WorkOrder> findRecentByCustomer(@Param("customerId") Long customerId, Pageable pageable);

    @Query("""
            select w from WorkOrder w join fetch w.vehicle
            where w.status in :statuses
              and not exists (
                select w2 from WorkOrder w2
                where w2.vehicle.id = w.vehicle.id and w2.status in :statuses
                  and coalesce(w2.deliveredAt, w2.closedAt, w2.openedAt)
                      > coalesce(w.deliveredAt, w.closedAt, w.openedAt)
              )
            """)
    List<WorkOrder> findLatestFinishedByVehicle(@Param("statuses") Collection<WorkOrderStatus> statuses);

    @Query("""
            select w from WorkOrder w join fetch w.customer
            left join fetch w.assignedUser
            where w.vehicle.id = :vehicleId
            """)
    List<WorkOrder> findRecentByVehicle(@Param("vehicleId") Long vehicleId, Pageable pageable);

    @Query("""
            select w from WorkOrder w join fetch w.customer join fetch w.vehicle
            where w.status in :statuses
            order by w.openedAt asc
            """)
    List<WorkOrder> findOpen(@Param("statuses") Collection<WorkOrderStatus> statuses, Pageable pageable);

    @Query("""
            select w from WorkOrder w join fetch w.customer join fetch w.vehicle
            left join fetch w.assignedUser
            where w.status in :statuses
              and ((w.closedAt >= :start and w.closedAt < :end)
                or (w.deliveredAt >= :start and w.deliveredAt < :end))
            order by coalesce(w.deliveredAt, w.closedAt) desc
            """)
    List<WorkOrder> findFinishedBetween(@Param("statuses") Collection<WorkOrderStatus> statuses,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    long countByStatus(WorkOrderStatus status);

    long countByStatusIn(Collection<WorkOrderStatus> statuses);

    @Query("""
            select max(w.openedAt) from WorkOrder w
            where w.vehicle.id = :vehicleId and w.status in :statuses
            """)
    LocalDateTime lastServiceDate(@Param("vehicleId") Long vehicleId,
                                  @Param("statuses") Collection<WorkOrderStatus> statuses);

    @Query("""
            select new tr.com.servisdefteri.workorder.WorkOrderStatusSummary(
                w.status, count(w), coalesce(sum(w.totalAmount), 0))
            from WorkOrder w
            where (cast(:start as timestamp) is null or w.openedAt >= :start)
              and (cast(:end as timestamp) is null or w.openedAt < :end)
            group by w.status
            order by w.status
            """)
    List<WorkOrderStatusSummary> statusSummary(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(w.totalAmount), 0) from WorkOrder w
            where w.status <> :cancelled
              and (cast(:start as timestamp) is null or w.openedAt >= :start)
              and (cast(:end as timestamp) is null or w.openedAt < :end)
            """)
    BigDecimal sumTotal(@Param("cancelled") WorkOrderStatus cancelled,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);
}
