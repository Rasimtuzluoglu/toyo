package tr.com.servisdefteri.workorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "work_order_item_templates")
@Getter
@Setter
@NoArgsConstructor
public class WorkOrderItemTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderItemType type;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "default_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultQuantity = BigDecimal.ONE;

    @Column(name = "default_unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal defaultUnitPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
