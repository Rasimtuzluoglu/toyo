package tr.com.servisdefteri.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tr.com.servisdefteri.customer.Customer;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 20)
    private String plate;

    @Column(name = "plate_normalized", nullable = false, length = 20, unique = true)
    private String plateNormalized;

    @Column(length = 50)
    private String brand;

    @Column(length = 50)
    private String model;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(length = 30)
    private String vin;

    @Column(name = "current_mileage", nullable = false)
    private int currentMileage;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    public String getDisplayName() {
        String brandModel = ((brand == null ? "" : brand) + " " + (model == null ? "" : model)).trim();
        return brandModel.isBlank() ? plate : plate + " · " + brandModel;
    }
}
