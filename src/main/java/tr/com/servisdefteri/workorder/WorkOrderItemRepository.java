package tr.com.servisdefteri.workorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface WorkOrderItemRepository extends JpaRepository<WorkOrderItem, Long> {

    @Query("""
            select i.type, coalesce(sum(i.lineTotal), 0)
            from WorkOrderItem i
            where i.workOrder.status <> :cancelled
              and (cast(:start as timestamp) is null or i.workOrder.openedAt >= :start)
              and (cast(:end as timestamp) is null or i.workOrder.openedAt < :end)
            group by i.type
            """)
    List<Object[]> totalsByType(@Param("cancelled") WorkOrderStatus cancelled,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(i.lineTotal), 0) from WorkOrderItem i
            where i.workOrder.id = :workOrderId
            """)
    BigDecimal sumByWorkOrder(@Param("workOrderId") Long workOrderId);
}
