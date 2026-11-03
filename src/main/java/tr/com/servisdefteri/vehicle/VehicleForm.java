package tr.com.servisdefteri.vehicle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tr.com.servisdefteri.common.validation.ValidPlate;

@Getter
@Setter
public class VehicleForm {

    @NotNull(message = "Araç sahibi seçilmelidir.")
    private Long customerId;

    @NotBlank(message = "Plaka zorunludur.")
    @ValidPlate
    @Size(max = 20, message = "Plaka en fazla 20 karakter olabilir.")
    private String plate;

    @Size(max = 50, message = "Marka en fazla 50 karakter olabilir.")
    private String brand;

    @Size(max = 50, message = "Model en fazla 50 karakter olabilir.")
    private String model;

    @Min(value = 1940, message = "Model yılı 1940'tan küçük olamaz.")
    @Max(value = 2100, message = "Model yılı 2100'den büyük olamaz.")
    private Integer modelYear;

    @Size(max = 30, message = "Şasi numarası en fazla 30 karakter olabilir.")
    private String vin;

    @NotNull(message = "Kilometre zorunludur.")
    @Min(value = 0, message = "Kilometre negatif olamaz.")
    @Max(value = 9_999_999, message = "Kilometre çok büyük.")
    private Integer currentMileage = 0;

    @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir.")
    private String note;

    private boolean active = true;
}
