package tr.com.servisdefteri.workorder;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class WorkOrderItemForm {

    @NotNull(message = "Kalem tipi seçilmelidir.")
    private WorkOrderItemType type = WorkOrderItemType.HIZMET;

    @NotBlank(message = "Açıklama zorunludur.")
    @Size(max = 300, message = "Açıklama en fazla 300 karakter olabilir.")
    private String description;

    @NotNull(message = "Miktar zorunludur.")
    @DecimalMin(value = "0.01", message = "Miktar sıfırdan büyük olmalıdır.")
    @Digits(integer = 10, fraction = 2, message = "Miktar en fazla 2 ondalık basamak içerebilir.")
    private BigDecimal quantity = BigDecimal.ONE;

    @NotNull(message = "Birim fiyat zorunludur.")
    @DecimalMin(value = "0.00", message = "Birim fiyat negatif olamaz.")
    @Digits(integer = 12, fraction = 2, message = "Birim fiyat en fazla 2 ondalık basamak içerebilir.")
    private BigDecimal unitPrice = BigDecimal.ZERO;
}
