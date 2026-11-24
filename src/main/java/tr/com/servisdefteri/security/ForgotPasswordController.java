package tr.com.servisdefteri.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tr.com.servisdefteri.common.BusinessRuleException;

import java.util.Objects;

/**
 * Giriş yapamayan kullanıcı için tek kullanımlık kurtarma koduyla parola sıfırlama.
 * Kod PATRON tarafından kullanıcı düzenleme ekranından üretilir.
 */
@Controller
@RequestMapping("/forgot-password")
public class ForgotPasswordController {

    private final UserService userService;
    private final LoginAttemptService loginAttemptService;

    public ForgotPasswordController(UserService userService, LoginAttemptService loginAttemptService) {
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "forgot-password";
    }

    @PostMapping
    public String reset(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                        BindingResult bindingResult,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {
        if (!Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Parolalar eşleşmiyor.");
        }
        if (bindingResult.hasErrors()) {
            return "forgot-password";
        }
        String ip = request.getRemoteAddr();
        if (loginAttemptService.isRecoveryBlocked(form.getUsername(), ip)) {
            bindingResult.reject("reset.locked",
                    "Çok fazla hatalı deneme. Lütfen 15 dakika sonra tekrar deneyin.");
            return "forgot-password";
        }
        try {
            userService.resetPasswordWithRecoveryCode(form.getUsername(), form.getCode(), form.getNewPassword());
            loginAttemptService.recoverySuccess(form.getUsername(), ip);
        } catch (BusinessRuleException ex) {
            loginAttemptService.recoveryFailure(form.getUsername(), ip);
            bindingResult.reject("reset.failed", ex.getMessage());
            return "forgot-password";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Parolanız sıfırlandı. Yeni parolanızla giriş yapabilirsiniz.");
        return "redirect:/login?reset";
    }
}
