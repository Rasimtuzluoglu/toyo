package tr.com.servisdefteri.common;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import tr.com.servisdefteri.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@ControllerAdvice
public class WebModelAdvice {

    private final TemplateFormat templateFormat;

    public WebModelAdvice(TemplateFormat templateFormat) {
        this.templateFormat = templateFormat;
    }

    @ModelAttribute("fmt")
    public TemplateFormat fmt() {
        return templateFormat;
    }

    @ModelAttribute("currentUser")
    public AppUserPrincipal currentUser(@AuthenticationPrincipal AppUserPrincipal principal) {
        return principal;
    }
}
