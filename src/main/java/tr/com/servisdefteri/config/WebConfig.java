package tr.com.servisdefteri.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class WebConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:Europe/Istanbul}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
