package tr.com.servisdefteri.common;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.settings.BusinessSettings;
import tr.com.servisdefteri.settings.BusinessSettingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@ControllerAdvice
public class WebModelAdvice {

    private final TemplateFormat templateFormat;
    private final BusinessSettingsService businessSettingsService;

    public WebModelAdvice(TemplateFormat templateFormat, BusinessSettingsService businessSettingsService) {
        this.templateFormat = templateFormat;
        this.businessSettingsService = businessSettingsService;
    }

    @ModelAttribute("fmt")
    public TemplateFormat fmt() {
        return templateFormat;
    }

    @ModelAttribute("brand")
    public BusinessSettings brand() {
        return businessSettingsService.get();
    }

    @ModelAttribute("currentUser")
    public AppUserPrincipal currentUser(@AuthenticationPrincipal AppUserPrincipal principal) {
        return principal;
    }
}
