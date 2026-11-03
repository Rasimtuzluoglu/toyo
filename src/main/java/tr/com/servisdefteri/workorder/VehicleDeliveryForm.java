package tr.com.servisdefteri.workorder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleDeliveryForm {

    @NotBlank(message = "Teslim alan kişi zorunludur.")
    @Size(max = 200, message = "Teslim alan kişi en fazla 200 karakter olabilir.")
    private String receiverName;

    @Min(value = 0, message = "Kilometre negatif olamaz.")
    @Max(value = 9_999_999, message = "Kilometre çok büyük.")
    private Integer mileage;

    @NotNull(message = "Yakıt seviyesi seçilmelidir.")
    private FuelLevel fuelLevel = FuelLevel.YARIM;

    @Size(max = 2000, message = "Teslim notu en fazla 2000 karakter olabilir.")
    private String notes;

    @Size(max = 900_000, message = "İmza verisi çok büyük.")
    private String signatureData;
}
