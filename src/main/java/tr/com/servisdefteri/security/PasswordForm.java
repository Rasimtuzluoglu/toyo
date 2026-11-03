package tr.com.servisdefteri.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import tr.com.servisdefteri.common.validation.StrongPassword;

@Getter
@Setter
public class PasswordForm {

    @NotBlank(message = "Yeni parola zorunludur.")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "Parola tekrarı zorunludur.")
    private String confirmPassword;
}
