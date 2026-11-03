package tr.com.servisdefteri.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tr.com.servisdefteri.common.validation.ValidPhone;

@Getter
@Setter
public class CustomerForm {

    @NotNull(message = "Müşteri türü seçilmelidir.")
    private CustomerType type = CustomerType.BIREYSEL;

    @NotBlank(message = "Ad veya firma unvanı zorunludur.")
    @Size(max = 200, message = "Ad veya firma unvanı en fazla 200 karakter olabilir.")
    private String name;

    @NotBlank(message = "Telefon zorunludur.")
    @ValidPhone
    @Size(max = 30, message = "Telefon en fazla 30 karakter olabilir.")
    private String phone;

    @Email(message = "Geçerli bir e-posta adresi giriniz.")
    @Size(max = 150, message = "E-posta en fazla 150 karakter olabilir.")
    private String email;

    @Size(max = 500, message = "Adres en fazla 500 karakter olabilir.")
    private String address;

    @Size(max = 100, message = "Vergi dairesi en fazla 100 karakter olabilir.")
    private String taxOffice;

    @Size(max = 20, message = "Vergi numarası en fazla 20 karakter olabilir.")
    private String taxNumber;

    @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir.")
    private String note;

    private boolean active = true;
}
