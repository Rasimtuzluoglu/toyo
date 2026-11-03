package tr.com.servisdefteri.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByEnabledTrueOrderByFullNameAsc();

    Optional<User> findFirstByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    @Modifying
    @Transactional
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    void updateLastLogin(@Param("id") Long id, @Param("at") LocalDateTime at);
}
