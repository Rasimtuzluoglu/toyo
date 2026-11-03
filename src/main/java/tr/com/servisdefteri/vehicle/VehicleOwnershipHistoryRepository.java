package tr.com.servisdefteri.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleOwnershipHistoryRepository extends JpaRepository<VehicleOwnershipHistory, Long> {

    List<VehicleOwnershipHistory> findByVehicleIdOrderByChangedAtDesc(Long vehicleId);
}
