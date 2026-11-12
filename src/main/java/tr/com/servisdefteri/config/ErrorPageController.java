package tr.com.servisdefteri.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorPageController {

    /**
     * Erişim reddi, güvenlik filtresi tarafından orijinal isteğin metoduyla
     * (ör. CSRF nedeniyle reddedilen POST) bu adrese forward edilir. Bu yüzden
     * yalnızca GET değil, tüm HTTP metotları karşılanmalıdır; aksi halde
     * HttpRequestMethodNotSupportedException oluşur ve kullanıcı 403 yerine 500 görür.
     */
    @RequestMapping("/403")
    public String forbidden(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return "error/403";
    }
}
