package tr.com.servisdefteri.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleDeliveryRepository extends JpaRepository<VehicleDelivery, Long> {

    Optional<VehicleDelivery> findByWorkOrderId(Long workOrderId);
}
