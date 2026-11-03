package tr.com.servisdefteri.workorder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleIntakeForm {

    @NotNull(message = "Yakıt seviyesi seçilmelidir.")
    private FuelLevel fuelLevel = FuelLevel.YARIM;

    @Size(max = 2000, message = "Hasar notu en fazla 2000 karakter olabilir.")
    private String damageNotes;

    @Size(max = 500, message = "Araçtaki eşyalar en fazla 500 karakter olabilir.")
    private String valuables;

    @Size(max = 900_000, message = "İmza verisi çok büyük.")
    private String signatureData;
}
