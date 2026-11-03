package tr.com.servisdefteri.security;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import tr.com.servisdefteri.common.BusinessRuleException;

@Controller
@RequestMapping("/setup")
public class SetupController {

    private final UserService userService;

    public SetupController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String show(Model model) {
        if (userService.isInitialized()) {
            return "redirect:/login";
        }
        model.addAttribute("setupForm", new SetupForm());
        return "setup";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("setupForm") SetupForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (userService.isInitialized()) {
            return "redirect:/login";
        }
        if (!java.util.Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Parolalar eşleşmiyor.");
        }
        if (bindingResult.hasErrors()) {
            return "setup";
        }
        try {
            userService.createInitialPatron(form.getUsername(), form.getPassword(), form.getFullName());
        } catch (BusinessRuleException ex) {
            bindingResult.reject("setup.failed", ex.getMessage());
            return "setup";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "İşletme sahibi (patron) hesabı oluşturuldu. Lütfen giriş yapın.");
        return "redirect:/login?setup";
    }
}
