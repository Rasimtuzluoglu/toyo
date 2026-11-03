package tr.com.servisdefteri.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select a from Appointment a
            join fetch a.customer join fetch a.vehicle
            where a.startsAt >= :start and a.startsAt < :end
            order by a.startsAt asc, a.id asc
            """)
    List<Appointment> findDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select a from Appointment a
            join fetch a.customer join fetch a.vehicle
            where a.startsAt >= :start and a.startsAt < :end
            order by a.startsAt asc, a.id asc
            """)
    List<Appointment> findBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select count(a) from Appointment a
            where a.vehicle.id = :vehicleId
              and a.startsAt = :startsAt
              and a.status <> :cancelled
              and (cast(:excludeId as long) is null or a.id <> :excludeId)
            """)
    long countConflict(@Param("vehicleId") Long vehicleId,
                       @Param("startsAt") LocalDateTime startsAt,
                       @Param("cancelled") AppointmentStatus cancelled,
                       @Param("excludeId") Long excludeId);
}
