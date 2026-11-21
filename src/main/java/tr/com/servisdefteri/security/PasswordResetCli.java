package tr.com.servisdefteri.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Host/container üzerinden acil parola sıfırlama aracı.
 * Uygulama arayüzüne erişim olmadan (ör. tek PATRON parolasını unuttuysa) çalıştırılır.
 *
 * Kullanım (container içinde):
 * java -Dloader.main=tr.com.servisdefteri.security.PasswordResetCli \
 *      -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher <kullanici> [yeniParola]
 */
public final class PasswordResetCli {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private PasswordResetCli() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            System.err.println("Kullanım: PasswordResetCli <kullanici> [yeniParola]");
            System.exit(2);
        }
        String username = args[0].trim().toLowerCase(Locale.ROOT);
        String password = args.length > 1 && !args[1].isBlank() ? args[1] : generatePassword();
        if (!PasswordPolicy.isStrong(password)) {
            System.err.println("Parola en az 10 karakter olmalı; büyük harf, küçük harf ve rakam içermelidir.");
            System.exit(3);
        }

        String url = env("DB_URL", "jdbc:postgresql://db:5432/servisdefteri");
        String dbUser = env("DB_USERNAME", env("POSTGRES_USER", "servisdefteri"));
        String dbPassword = env("DB_PASSWORD", env("POSTGRES_PASSWORD", ""));
        String hash = new BCryptPasswordEncoder(12).encode(password);

        try {
            Class.forName("org.postgresql.Driver");
            try (Connection connection = DriverManager.getConnection(url, dbUser, dbPassword)) {
                Long userId;
                String role;
                try (PreparedStatement select = connection.prepareStatement(
                        "select id, role from users where lower(username) = ?")) {
                    select.setString(1, username);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            System.err.println("Kullanıcı bulunamadı: " + username);
                            System.exit(4);
                        }
                        userId = result.getLong("id");
                        role = result.getString("role");
                    }
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "update users set password_hash = ?, recovery_code_hash = null, "
                                + "recovery_code_expires_at = null, recovery_code_created_at = null "
                                + "where id = ?")) {
                    update.setString(1, hash);
                    update.setLong(2, userId);
                    update.executeUpdate();
                }

                try (PreparedStatement audit = connection.prepareStatement(
                        "insert into audit_logs (actor_id, actor_username, action, entity_type, "
                                + "entity_id, details, ip_address, created_at) "
                                + "values (null, 'SYSTEM-CLI', 'USER_PASSWORD_RESET', 'Kullanıcı', ?, ?, "
                                + "'127.0.0.1', now())")) {
                    audit.setLong(1, userId);
                    audit.setString(2, "Acil parola sıfırlama (host CLI): " + username);
                    audit.executeUpdate();
                }

                System.out.println("Parola güncellendi. Kullanıcı: " + username + " (rol: " + role + ")");
                if (args.length < 2 || args[1].isBlank()) {
                    System.out.println("Yeni geçici parola: " + password);
                }
                System.out.println("Açık oturumların kapanması için uygulamayı yeniden başlatın.");
            }
        } catch (ClassNotFoundException | SQLException ex) {
            System.err.println("Parola sıfırlanamadı: " + ex.getMessage());
            System.exit(5);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String generatePassword() {
        while (true) {
            StringBuilder value = new StringBuilder(16);
            for (int i = 0; i < 16; i++) {
                value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            // En az bir büyük harf, küçük harf ve rakam garantisi.
            value.setCharAt(0, (char) ('A' + RANDOM.nextInt(26)));
            value.setCharAt(1, (char) ('a' + RANDOM.nextInt(26)));
            value.setCharAt(2, (char) ('2' + RANDOM.nextInt(8)));
            String candidate = value.toString();
            if (PasswordPolicy.isStrong(candidate)) {
                return candidate;
            }
        }
    }
}
