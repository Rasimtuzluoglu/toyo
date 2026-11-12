package tr.com.servisdefteri.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    public ModelAndView business(BusinessRuleException ex, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("error/business");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        mav.addObject("errorTitle", "İşlem tamamlanamadı");
        mav.addObject("errorMessage", ex.getMessage());
        mav.addObject("currentPath", request.getRequestURI());
        return mav;
    }

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException ex) {
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("errorMessage", ex.getMessage());
        return mav;
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ModelAndView missingPage() {
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("errorMessage", "Aradığınız sayfa mevcut değil.");
        return mav;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView dataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Veri bütünlüğü ihlali: {}", ex.getMostSpecificCause().getMessage());
        ModelAndView mav = new ModelAndView("error/business");
        mav.setStatus(HttpStatus.CONFLICT);
        mav.addObject("errorTitle", "Kayıt çakışması");
        mav.addObject("errorMessage", "Bu kayıt başka bir işlemle çakışıyor olabilir. "
                + "Aynı plaka, şasi veya kullanıcı adının daha önce kullanılmadığını kontrol edin.");
        return mav;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView accessDenied() {
        ModelAndView mav = new ModelAndView("error/403");
        mav.setStatus(HttpStatus.FORBIDDEN);
        return mav;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView uploadTooLarge() {
        ModelAndView mav = new ModelAndView("error/business");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        mav.addObject("errorTitle", "Dosya çok büyük");
        mav.addObject("errorMessage", "Yüklenen dosya izin verilen boyutu aşıyor (en fazla 2 MB).");
        return mav;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView badParameter() {
        ModelAndView mav = new ModelAndView("error/business");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        mav.addObject("errorTitle", "Geçersiz istek");
        mav.addObject("errorMessage", "Gönderilen parametre geçersiz veya eksik.");
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex) {
        log.error("Beklenmeyen hata", ex);
        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }
}
