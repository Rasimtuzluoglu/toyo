package tr.com.servisdefteri.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.initial-admin")
public record InitialAdminProperties(String username, String password, String fullName) {
}
