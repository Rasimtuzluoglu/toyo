package tr.com.servisdefteri.security;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;
import tr.com.servisdefteri.common.BusinessRuleException;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("userForm", new UserCreateForm());
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserCreateForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!java.util.Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Parolalar eşleşmiyor.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        try {
            userService.create(form);
        } catch (BusinessRuleException ex) {
            bindingResult.reject("user.failed", ex.getMessage());
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Kullanıcı oluşturuldu.");
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        UserEditForm form = new UserEditForm();
        form.setFullName(user.getFullName());
        form.setRole(user.getRole());
        form.setEnabled(user.isEnabled());
        model.addAttribute("user", user);
        model.addAttribute("userForm", form);
        model.addAttribute("roles", Role.values());
        return "users/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserEditForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", userService.findById(id));
            model.addAttribute("roles", Role.values());
            return "users/edit";
        }
        try {
            userService.update(id, form);
        } catch (BusinessRuleException ex) {
            bindingResult.reject("user.failed", ex.getMessage());
            model.addAttribute("user", userService.findById(id));
            model.addAttribute("roles", Role.values());
            return "users/edit";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Kullanıcı güncellendi.");
        return "redirect:/users";
    }

    @GetMapping("/{id}/password")
    public String passwordForm(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.findById(id));
        model.addAttribute("passwordForm", new PasswordForm());
        return "users/password";
    }

    @PostMapping("/{id}/password")
    public String changePassword(@PathVariable Long id,
                                 @Valid @ModelAttribute("passwordForm") PasswordForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!java.util.Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Parolalar eşleşmiyor.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", userService.findById(id));
            return "users/password";
        }
        userService.changePassword(id, form.getNewPassword());
        redirectAttributes.addFlashAttribute("successMessage", "Parola güncellendi.");
        return "redirect:/users";
    }
}
