package tr.com.servisdefteri.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import tr.com.servisdefteri.common.validation.ValidPhone;

@Getter
@Setter
public class BusinessSettingsForm {

    @NotBlank(message = "İşletme adı zorunludur.")
    @Size(max = 200, message = "İşletme adı en fazla 200 karakter olabilir.")
    private String name;

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

    @Size(max = 120, message = "Slogan en fazla 120 karakter olabilir.")
    private String tagline;

    @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "Ana renk #RRGGBB biçiminde olmalıdır.")
    private String primaryColor = "#eb0a1e";

    private MultipartFile logo;

    private boolean removeLogo;
}
