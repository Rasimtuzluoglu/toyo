package tr.com.servisdefteri.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.support.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIT extends IntegrationTestSupport {

    private User patron;
    private User muhasebe;
    private User servis;

    @BeforeEach
    void createUsers() {
        patron = createUser("patron", "İşletme Sahibi", Role.PATRON);
        muhasebe = createUser("muhasebe", "Muhasebe Kullanıcısı", Role.MUHASEBE);
        servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        jdbcTemplate.update("update installation_state set setup_completed = true, completed_at = now()");
    }

    private void resetSetupState() {
        jdbcTemplate.update("update installation_state set setup_completed = false, completed_at = null");
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void firstRunRedirectsRootAndLoginToSetup() throws Exception {
        resetSetupState();

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup"));
        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup"));
    }

    @Test
    void userCanChangeOwnPassword() throws Exception {
        mockMvc.perform(get("/profile/password").with(user(principal(patron))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/profile/password").with(user(principal(patron))).with(csrf())
                        .param("currentPassword", "TestPass1234")
                        .param("newPassword", "NewPass1234")
                        .param("confirmPassword", "NewPass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        User updated = userRepository.findByUsernameIgnoreCase("patron").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass1234", updated.getPasswordHash())).isTrue();
    }

    @Test
    void patronCanUpdateBusinessSettings() throws Exception {
        mockMvc.perform(post("/settings/business").with(user(principal(patron))).with(csrf())
                        .param("name", "Öztoprak Test Servis")
                        .param("phone", "0555 111 22 33")
                        .param("email", "servis@example.local")
                        .param("address", "Ornek Mahalle")
                        .param("taxOffice", "Ornek")
                        .param("taxNumber", "1234567890"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        String name = jdbcTemplate.queryForObject(
                "select name from business_settings where id = 1", String.class);
        assertThat(name).isEqualTo("Öztoprak Test Servis");
    }

    @Test
    void patronCanReachEveryModule() throws Exception {
        String[] pages = {
                "/", "/work-orders/board", "/customers", "/customers/new",
                "/vehicles", "/vehicles/new", "/work-orders", "/work-orders/new",
                "/accounts", "/payments", "/reports", "/users", "/settings", "/settings/audit"
        };
        for (String page : pages) {
            mockMvc.perform(get(page).with(user(principal(patron))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void servisCanManageOperationsButCannotReachFinances() throws Exception {
        String[] allowed = {
                "/work-orders/board", "/customers", "/customers/new",
                "/vehicles", "/vehicles/new", "/work-orders", "/work-orders/new"
        };
        for (String page : allowed) {
            mockMvc.perform(get(page).with(user(principal(servis))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/").with(user(principal(servis))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders/board"));

        String[] forbidden = {
                "/accounts", "/payments", "/reports", "/users", "/settings", "/settings/audit"
        };
        for (String page : forbidden) {
            mockMvc.perform(get(page).with(user(principal(servis))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void muhasebeCanManageFinancesButCannotCreateOperationalRecords() throws Exception {
        String[] allowed = {
                "/", "/work-orders/board", "/customers", "/vehicles", "/work-orders",
                "/accounts", "/payments", "/payments/new", "/reports"
        };
        for (String page : allowed) {
            mockMvc.perform(get(page).with(user(principal(muhasebe))))
                    .andExpect(status().isOk());
        }

        String[] forbidden = {
                "/customers/new", "/vehicles/new", "/work-orders/new", "/accounts/new-debt",
                "/users", "/settings", "/settings/audit"
        };
        for (String page : forbidden) {
            mockMvc.perform(get(page).with(user(principal(muhasebe))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void muhasebeCannotPostOperationalRecords() throws Exception {
        mockMvc.perform(post("/work-orders").with(user(principal(muhasebe))).with(csrf())
                        .param("customerId", "1")
                        .param("vehicleId", "1")
                        .param("complaint", "Yetkisiz deneme"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/customers").with(user(principal(muhasebe))).with(csrf())
                        .param("type", "BIREYSEL")
                        .param("name", "Yetkisiz Müşteri")
                        .param("phone", "0555 111 22 33"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/customers")
                        .with(user(principal(patron)))
                        .param("type", "BIREYSEL")
                        .param("name", "CSRF Test")
                        .param("phone", "0555 111 22 33"))
                .andExpect(status().isForbidden());
    }

    @Test
    void successfulLoginOpensSessionAndUpdatesLastLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "patron")
                        .param("password", "TestPass1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/").session(session)).andExpect(status().isOk());

        User refreshed = userRepository.findByUsernameIgnoreCase("patron").orElseThrow();
        assertThat(refreshed.getLastLoginAt()).isNotNull();
    }

    @Test
    void failedLoginUsesGenericErrorPage() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "patron")
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void setupIsOnlyAvailableBeforeFirstInstallation() throws Exception {
        mockMvc.perform(get("/setup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        resetSetupState();
        mockMvc.perform(get("/setup"))
                .andExpect(status().isOk());
    }

    @Test
    void setupCreatesFirstPatronAndCannotRunTwice() throws Exception {
        resetSetupState();

        mockMvc.perform(post("/setup").with(csrf())
                        .param("username", "ilkpatron")
                        .param("fullName", "İlk İşletme Sahibi")
                        .param("password", "FirstAdmin1234")
                        .param("confirmPassword", "FirstAdmin1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?setup"));

        User created = userRepository.findByUsernameIgnoreCase("ilkpatron").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.PATRON);

        Boolean completed = jdbcTemplate.queryForObject(
                "select setup_completed from installation_state where id = 1", Boolean.class);
        assertThat(completed).isTrue();

        // Kullanıcı tablosu boşalsa bile kurulum bir daha açılmaz.
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
        mockMvc.perform(get("/setup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/setup").with(csrf())
                        .param("username", "ikinci")
                        .param("fullName", "İkinci Patron")
                        .param("password", "SecondAdmin1234")
                        .param("confirmPassword", "SecondAdmin1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        Long userCount = jdbcTemplate.queryForObject("select count(*) from users", Long.class);
        assertThat(userCount).isZero();
    }
}
