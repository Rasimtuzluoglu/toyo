package tr.com.servisdefteri.backup;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {

    List<BackupRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<BackupRun> findFirstByStatusOrderByStartedAtDesc(BackupStatus status);
}
