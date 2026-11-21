package tr.com.servisdefteri.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsernameChangeForm {

    @NotBlank(message = "Yeni kullanıcı adı zorunludur.")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
            message = "Kullanıcı adı 3-50 karakter olmalı; harf, rakam, nokta, tire ve alt çizgi içerebilir.")
    private String newUsername;

    @NotBlank(message = "Mevcut parola zorunludur.")
    private String currentPassword;
}
