package tr.com.servisdefteri.appointment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class AppointmentForm {

    @NotNull(message = "Müşteri seçilmelidir.")
    private Long customerId;

    @NotNull(message = "Araç seçilmelidir.")
    private Long vehicleId;

    @NotNull(message = "Randevu tarihi ve saati zorunludur.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;

    private AppointmentStatus status = AppointmentStatus.PLANLANDI;

    @Size(max = 1000, message = "Not en fazla 1000 karakter olabilir.")
    private String notes;
}
