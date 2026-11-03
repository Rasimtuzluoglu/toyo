package tr.com.servisdefteri.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleIntakeRepository extends JpaRepository<VehicleIntake, Long> {

    Optional<VehicleIntake> findByWorkOrderId(Long workOrderId);
}
