package tr.com.servisdefteri.workorder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tr.com.servisdefteri.common.validation.ValidPhone;
import tr.com.servisdefteri.common.validation.ValidPlate;

@Getter
@Setter
public class QuickWorkOrderForm {

    @NotBlank(message = "Plaka zorunludur.")
    @ValidPlate
    @Size(max = 20, message = "Plaka en fazla 20 karakter olabilir.")
    private String plate;

    @NotBlank(message = "Müşteri adı zorunludur.")
    @Size(max = 200, message = "Müşteri adı en fazla 200 karakter olabilir.")
    private String customerName;

    @NotBlank(message = "Telefon zorunludur.")
    @ValidPhone
    @Size(max = 30, message = "Telefon en fazla 30 karakter olabilir.")
    private String phone;

    @Size(max = 50, message = "Marka en fazla 50 karakter olabilir.")
    private String brand;

    @Size(max = 50, message = "Model en fazla 50 karakter olabilir.")
    private String model;

    @Min(value = 0, message = "Kilometre negatif olamaz.")
    @Max(value = 9_999_999, message = "Kilometre çok büyük.")
    private Integer mileage;

    @Size(max = 2000, message = "Şikâyet en fazla 2000 karakter olabilir.")
    private String complaint;
}
