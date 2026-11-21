package tr.com.servisdefteri.security;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public ProfileController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/password")
    public String passwordForm(Model model) {
        model.addAttribute("passwordForm", new PasswordChangeForm());
        model.addAttribute("activeMenu", "");
        return "profile/password";
    }

    @GetMapping("/username")
    public String usernameForm(Model model) {
        model.addAttribute("usernameForm", new UsernameChangeForm());
        model.addAttribute("currentUsername", currentUserService.username());
        model.addAttribute("activeMenu", "");
        return "profile/username";
    }

    @PostMapping("/username")
    public String changeUsername(@Valid @ModelAttribute("usernameForm") UsernameChangeForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("currentUsername", currentUserService.username());
            model.addAttribute("activeMenu", "");
            return "profile/username";
        }
        try {
            userService.changeOwnUsername(currentUserService.id(),
                    form.getNewUsername(), form.getCurrentPassword());
        } catch (BusinessRuleException ex) {
            bindingResult.reject("username.failed", ex.getMessage());
            model.addAttribute("currentUsername", currentUserService.username());
            model.addAttribute("activeMenu", "");
            return "profile/username";
        }
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return "redirect:/login?usernameChanged";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") PasswordChangeForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Parolalar eşleşmiyor.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("activeMenu", "");
            return "profile/password";
        }
        try {
            userService.changeOwnPassword(currentUserService.id(),
                    form.getCurrentPassword(), form.getNewPassword());
        } catch (BusinessRuleException ex) {
            bindingResult.rejectValue("currentPassword", "password.current", ex.getMessage());
            model.addAttribute("activeMenu", "");
            return "profile/password";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Parolanız güncellendi.");
        return "redirect:/";
    }
}
