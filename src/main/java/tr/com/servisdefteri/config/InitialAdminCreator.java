package tr.com.servisdefteri.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tr.com.servisdefteri.security.UserService;

/**
 * Ortam degiskenleri verilmisse ilk yoneticiyi olusturur.
 * Verilmemisse uygulama /setup ekrani uzerinden ilk yonetici kurulumuna izin verir.
 * Varsayilan veya herkesce bilinen bir parola asla kullanilmaz.
 */
@Component
public class InitialAdminCreator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminCreator.class);

    private final UserService userService;
    private final InitialAdminProperties properties;

    public InitialAdminCreator(UserService userService, InitialAdminProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.isInitialized()) {
            return;
        }
        if (isBlank(properties.username()) || isBlank(properties.password())) {
            log.warn("Sistemde kullanıcı yok. İlk işletme sahibini oluşturmak için tarayıcıdan /setup adresini açın "
                    + "veya INITIAL_ADMIN_USERNAME / INITIAL_ADMIN_PASSWORD ortam değişkenlerini tanımlayın.");
            return;
        }
        try {
            String fullName = isBlank(properties.fullName()) ? "İşletme Sahibi" : properties.fullName();
            userService.createInitialPatron(properties.username(), properties.password(), fullName);
            log.info("İlk işletme sahibi (patron) hesabı ortam değişkenlerinden oluşturuldu: {}",
                    properties.username());
        } catch (RuntimeException ex) {
            log.error("İlk işletme sahibi oluşturulamadı. /setup ekranını kullanabilirsiniz. Sebep: {}",
                    ex.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
