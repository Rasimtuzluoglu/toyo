package tr.com.servisdefteri.settings;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import tr.com.servisdefteri.settings.BusinessSettings;
import tr.com.servisdefteri.settings.BusinessSettingsService;

@Controller
public class BrandingController {

    private final BusinessSettingsService businessSettingsService;

    public BrandingController(BusinessSettingsService businessSettingsService) {
        this.businessSettingsService = businessSettingsService;
    }

    @GetMapping(value = "/branding/theme.css", produces = "text/css")
    public ResponseEntity<String> theme() {
        BusinessSettings settings = businessSettingsService.get();
        String primary = settings.getPrimaryColor() == null ? "#eb0a1e" : settings.getPrimaryColor();
        String dark = settings.getPrimaryColorDark();
        String css = ":root{--sd-accent:" + primary + ";--sd-accent-dark:" + dark
                + ";--sd-gold:" + primary + ";}";
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/css"))
                .cacheControl(CacheControl.noCache())
                .body(css);
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<byte[]> logo() {
        BusinessSettings settings = businessSettingsService.get();
        if (settings.getLogoData() == null || settings.getLogoData().length == 0) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = settings.getLogoContentType() == null
                ? MediaType.IMAGE_PNG
                : MediaType.parseMediaType(settings.getLogoContentType());
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noCache())
                .body(settings.getLogoData());
    }
}
