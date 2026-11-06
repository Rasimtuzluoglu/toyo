package tr.com.servisdefteri.vehicle;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Query(value = """
            select v from Vehicle v join fetch v.customer c
            where (cast(:q as string) is null
                   or lower(function('translate', v.plate, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                   or lower(v.plateNormalized) like :q
                   or lower(v.plateNormalized) like replace(cast(:q as string), ' ', '')
                   or lower(function('translate', v.brand, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                   or lower(function('translate', v.model, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                   or lower(function('translate', c.name, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                   or lower(c.phone) like :q
                   or c.phoneNormalized like :q)
              and (cast(:active as boolean) is null or v.active = :active)
              and (cast(:customerId as long) is null or c.id = :customerId)
            """,
            countQuery = """
                    select count(v) from Vehicle v join v.customer c
                    where (cast(:q as string) is null
                           or lower(function('translate', v.plate, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                           or lower(v.plateNormalized) like :q
                           or lower(v.plateNormalized) like replace(cast(:q as string), ' ', '')
                           or lower(function('translate', v.brand, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                           or lower(function('translate', v.model, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                           or lower(function('translate', c.name, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                           or lower(c.phone) like :q
                           or c.phoneNormalized like :q)
                      and (cast(:active as boolean) is null or v.active = :active)
                      and (cast(:customerId as long) is null or c.id = :customerId)
                    """)
    Page<Vehicle> search(@Param("q") String q,
                         @Param("active") Boolean active,
                         @Param("customerId") Long customerId,
                         Pageable pageable);

    @Query("select v from Vehicle v join fetch v.customer where v.id = :id")
    Optional<Vehicle> findDetailedById(@Param("id") Long id);

    @Query("select v from Vehicle v join fetch v.customer where v.plateNormalized = :plateNormalized")
    Optional<Vehicle> findByPlateNormalized(@Param("plateNormalized") String plateNormalized);

    Optional<Vehicle> findByPlateNormalizedIgnoreCase(String plateNormalized);

    List<Vehicle> findByCustomerIdOrderByPlateAsc(Long customerId);

    List<Vehicle> findByActiveTrueOrderByPlateAsc();

    long countByActiveTrue();

    long countByCustomerIdAndActiveTrue(Long customerId);

    boolean existsByPlateNormalized(String plateNormalized);

    boolean existsByPlateNormalizedAndIdNot(String plateNormalized, Long id);

    boolean existsByVinAndIdNot(String vin, Long id);

    boolean existsByVin(String vin);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v join fetch v.customer where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);
}
