package tr.com.servisdefteri.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("""
            select c from Customer c
            where (cast(:q as string) is null
                   or lower(function('translate', c.name, 'İIıŞşĞğÜüÖöÇç', 'iiissgguuoocc')) like :q
                   or c.phoneNormalized like :phoneLike)
              and (cast(:active as boolean) is null or c.active = :active)
              and (cast(:type as string) is null or c.type = :type)
            """)
    Page<Customer> search(@Param("q") String q,
                          @Param("phoneLike") String phoneLike,
                          @Param("active") Boolean active,
                          @Param("type") CustomerType type,
                          Pageable pageable);

    boolean existsByPhoneNormalizedAndNameIgnoreCase(String phoneNormalized, String name);

    java.util.Optional<Customer> findFirstByPhoneNormalizedAndNameIgnoreCase(String phoneNormalized, String name);

    boolean existsByPhoneNormalizedAndNameIgnoreCaseAndIdNot(String phoneNormalized, String name, Long id);

    List<Customer> findByActiveTrueOrderByNameAsc();

    List<Customer> findAllByOrderByNameAsc();

    long countByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);
}
