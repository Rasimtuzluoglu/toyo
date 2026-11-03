package tr.com.servisdefteri.backup;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupRunRepository extends JpaRepository<BackupRun, Long> {

    List<BackupRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
