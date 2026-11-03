package tr.com.servisdefteri.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserEditForm {

    @NotBlank(message = "Ad soyad zorunludur.")
    @Size(max = 100, message = "Ad soyad en fazla 100 karakter olabilir.")
    private String fullName;

    @NotNull(message = "Rol seçilmelidir.")
    private Role role;

    private boolean enabled = true;
}
