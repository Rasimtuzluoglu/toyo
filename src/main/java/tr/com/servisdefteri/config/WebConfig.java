package tr.com.servisdefteri.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public Clock clock(@Value("${app.timezone:Europe/Istanbul}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("login");
    }
}
