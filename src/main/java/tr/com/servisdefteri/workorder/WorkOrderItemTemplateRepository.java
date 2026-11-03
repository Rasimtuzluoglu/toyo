package tr.com.servisdefteri.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderItemTemplateRepository extends JpaRepository<WorkOrderItemTemplate, Long> {

    List<WorkOrderItemTemplate> findByActiveTrueOrderBySortOrderAscNameAsc();
}
