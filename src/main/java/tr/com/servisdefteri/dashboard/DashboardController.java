package tr.com.servisdefteri.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        if (principal.getRole() == Role.SERVIS) {
            return "redirect:/work-orders/board";
        }
        boolean financial = principal.getRole() == Role.PATRON
                || principal.getRole() == Role.MUHASEBE;
        model.addAttribute("view", dashboardService.load(financial));
        model.addAttribute("activeMenu", "dashboard");
        if (principal.getRole() == Role.MUHASEBE) {
            return "dashboard/finance";
        }
        return "dashboard/index";
    }
}
