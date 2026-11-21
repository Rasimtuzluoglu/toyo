package tr.com.servisdefteri.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tr.com.servisdefteri.common.validation.StrongPassword;

@Getter
@Setter
public class ForgotPasswordForm {

    @NotBlank(message = "Kullanıcı adı zorunludur.")
    @Size(max = 50, message = "Kullanıcı adı en fazla 50 karakter olabilir.")
    private String username;

    @NotBlank(message = "Kurtarma kodu zorunludur.")
    @Size(max = 32, message = "Kurtarma kodu geçersiz.")
    private String code;

    @NotBlank(message = "Yeni parola zorunludur.")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "Parola tekrarı zorunludur.")
    private String confirmPassword;
}
