package tr.com.servisdefteri.security;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tr.com.servisdefteri.common.BusinessRuleException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Controller
@RequestMapping("/setup")
public class SetupController {

    private final UserService userService;
    private final String configuredToken;

    public SetupController(UserService userService,
                           @Value("${app.setup.token:}") String configuredToken) {
        this.userService = userService;
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    @GetMapping
    public String show(@RequestParam(name = "token", required = false) String token,
                       Model model, HttpServletResponse response) {
        if (userService.isInitialized()) {
            return "redirect:/login";
        }
        if (!tokenMatches(token)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return "error/403";
        }
        model.addAttribute("setupForm", new SetupForm());
        model.addAttribute("setupToken", token);
        return "setup";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("setupForm") SetupForm form,
                         BindingResult bindingResult,
                         @RequestParam(name = "token", required = false) String token,
                         Model model,
                         HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        if (userService.isInitialized()) {
            return "redirect:/login";
        }
        if (!tokenMatches(token)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return "error/403";
        }
        model.addAttribute("setupToken", token);
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

    /**
     * SETUP_TOKEN tanımlıysa kurulum ekranı yalnızca bu token ile açılabilir.
     * Tanımlı değilse eski davranış korunur (yalnızca loopback portuna bağlı kurulum).
     */
    boolean tokenMatches(String provided) {
        if (configuredToken.isEmpty()) {
            return true;
        }
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(configuredToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
