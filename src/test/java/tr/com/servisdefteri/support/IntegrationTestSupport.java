package tr.com.servisdefteri.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tr.com.servisdefteri.security.AppUserPrincipal;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.security.UserRepository;

import java.time.LocalDateTime;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE payment_allocations, account_transactions, payments,
                    work_order_items, work_orders, vehicle_ownership_history,
                    vehicles, customers, audit_logs, backup_runs, users
                RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.execute("ALTER SEQUENCE work_order_no_seq RESTART WITH 1");
        jdbcTemplate.execute("ALTER SEQUENCE payment_receipt_seq RESTART WITH 1");
    }

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    protected User createUser(String username, String fullName, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode("TestPass1234"));
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    protected AppUserPrincipal principal(User user) {
        return AppUserPrincipal.from(user);
    }
}
