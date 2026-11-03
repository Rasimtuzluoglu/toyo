package tr.com.servisdefteri.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tr.com.servisdefteri.common.validation.StrongPassword;

@Getter
@Setter
public class SetupForm {

    @NotBlank(message = "Kullanıcı adı zorunludur.")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
            message = "Kullanıcı adı 3-50 karakter olmalı; harf, rakam, nokta, tire ve alt çizgi içerebilir.")
    private String username;

    @NotBlank(message = "Ad soyad zorunludur.")
    @Size(max = 100, message = "Ad soyad en fazla 100 karakter olabilir.")
    private String fullName;

    @NotBlank(message = "Parola zorunludur.")
    @StrongPassword
    private String password;

    @NotBlank(message = "Parola tekrarı zorunludur.")
    private String confirmPassword;
}
