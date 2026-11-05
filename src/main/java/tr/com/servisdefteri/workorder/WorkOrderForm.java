package tr.com.servisdefteri.workorder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class WorkOrderForm {

    @Size(max = 30, message = "İş emri numarası en fazla 30 karakter olabilir.")
    private String orderNo;

    @NotNull(message = "Müşteri seçilmelidir.")
    private Long customerId;

    @NotNull(message = "Araç seçilmelidir.")
    private Long vehicleId;

    private Long assignedUserId;

    @NotNull(message = "Açılış tarihi zorunludur.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime openedAt;

    @Size(max = 2000, message = "Şikâyet en fazla 2000 karakter olabilir.")
    private String complaint;

    @Size(max = 2000, message = "Tespit en fazla 2000 karakter olabilir.")
    private String diagnosis;

    @Size(max = 2000, message = "Yapılan işlemler en fazla 2000 karakter olabilir.")
    private String workDone;

    @Min(value = 0, message = "Kilometre negatif olamaz.")
    @Max(value = 9_999_999, message = "Kilometre çok büyük.")
    private Integer mileage;

    @Size(max = 2000, message = "Açıklama en fazla 2000 karakter olabilir.")
    private String notes;
}
