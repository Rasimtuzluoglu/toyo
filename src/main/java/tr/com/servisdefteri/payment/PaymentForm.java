package tr.com.servisdefteri.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PaymentForm {

    @NotNull(message = "Müşteri seçilmelidir.")
    private Long customerId;

    @NotNull(message = "Tutar zorunludur.")
    @DecimalMin(value = "0.01", message = "Tutar sıfırdan büyük olmalıdır.")
    @Digits(integer = 12, fraction = 2, message = "Tutar en fazla 2 ondalık basamak içerebilir.")
    private BigDecimal amount;

    @NotNull(message = "Tahsilat tarihi zorunludur.")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate paymentDate;

    @NotNull(message = "Ödeme yöntemi seçilmelidir.")
    private PaymentMethod method = PaymentMethod.NAKIT;

    @Size(max = 500, message = "Açıklama en fazla 500 karakter olabilir.")
    private String description;

    private List<AllocationForm> allocations = new ArrayList<>();
}
