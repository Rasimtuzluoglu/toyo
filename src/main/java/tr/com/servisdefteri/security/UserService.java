package tr.com.servisdefteri.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] RECOVERY_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int RECOVERY_CODE_LENGTH = 10;
    private static final Duration RECOVERY_CODE_TTL = Duration.ofHours(24);
    /** Geçersiz kullanıcı adlarında da BCrypt maliyeti oluşturur; kullanıcı adı zamanlama sızıntısını azaltır. */
    private static final String DUMMY_RECOVERY_HASH =
            new BCryptPasswordEncoder(12).encode("dummy-recovery-code-value");

    private final UserRepository userRepository;
    private final InstallationStateRepository installationStateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final SessionRevocationService sessionRevocationService;
    private final Clock clock;

    public UserService(UserRepository userRepository,
                       InstallationStateRepository installationStateRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService, CurrentUserService currentUserService,
                       SessionRevocationService sessionRevocationService, Clock clock) {
        this.userRepository = userRepository;
        this.installationStateRepository = installationStateRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.sessionRevocationService = sessionRevocationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isInitialized() {
        boolean setupCompleted = installationStateRepository.findById(InstallationState.SINGLETON_ID)
                .map(InstallationState::isSetupCompleted)
                .orElse(false);
        return setupCompleted || userRepository.count() > 0;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll(org.springframework.data.domain.Sort.by("fullName"));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("Kullanıcı", id));
    }

    @Transactional
    public User createInitialPatron(String username, String rawPassword, String fullName) {
        InstallationState state = installationStateRepository.findForUpdate()
                .orElseThrow(() -> new BusinessRuleException("Kurulum durumu bulunamadı."));
        if (state.isSetupCompleted() || userRepository.count() > 0) {
            throw new BusinessRuleException("Kurulum daha önce tamamlanmış.");
        }
        PasswordPolicy.requireStrong(rawPassword);
        User user = new User();
        user.setUsername(normalizeUsername(username));
        user.setFullName(fullName.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.PATRON);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now(clock));
        User saved = userRepository.save(user);
        state.setSetupCompleted(true);
        state.setCompletedAt(LocalDateTime.now(clock));
        installationStateRepository.save(state);
        auditService.recordAs(saved.getId(), saved.getUsername(), AuditAction.USER_CREATED,
                "Kullanıcı", saved.getId(), "İlk işletme sahibi (patron) hesabı oluşturuldu");
        return saved;
    }

    @Transactional
    public User create(UserCreateForm form) {
        String username = normalizeUsername(form.getUsername());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessRuleException("Bu kullanıcı adı zaten kullanılıyor.");
        }
        PasswordPolicy.requireStrong(form.getPassword());
        User user = new User();
        user.setUsername(username);
        user.setFullName(form.getFullName().trim());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setRole(form.getRole());
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now(clock));
        User saved = userRepository.save(user);
        auditService.record(AuditAction.USER_CREATED, "Kullanıcı", saved.getId(),
                "Kullanıcı oluşturuldu: " + saved.getUsername() + ", rol=" + saved.getRole());
        return saved;
    }

    @Transactional
    public User update(Long id, UserEditForm form) {
        User user = findById(id);
        boolean self = currentUserService.id().equals(user.getId());
        boolean lastEnabledPatron = user.getRole() == Role.PATRON && user.isEnabled()
                && userRepository.countByRoleAndEnabledTrue(Role.PATRON) <= 1;

        if (self && !form.isEnabled()) {
            throw new BusinessRuleException("Kendi hesabınızı pasife alamazsınız.");
        }
        if (self && form.getRole() != Role.PATRON) {
            throw new BusinessRuleException("Kendi işletme sahibi rolünüzü değiştiremezsiniz.");
        }
        if (lastEnabledPatron && (form.getRole() != Role.PATRON || !form.isEnabled())) {
            throw new BusinessRuleException("Sistemde en az bir aktif işletme sahibi (patron) kalmalıdır.");
        }

        String oldUsername = user.getUsername();
        boolean usernameChanged = false;
        String requestedUsername = normalizeUsername(form.getUsername());
        if (!requestedUsername.isBlank() && !requestedUsername.equals(user.getUsername())) {
            if (!currentUserService.isPatron()) {
                throw new BusinessRuleException("Kullanıcı adı değiştirme yetkiniz yok.");
            }
            validateUsername(requestedUsername);
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(requestedUsername, id)) {
                throw new BusinessRuleException("Bu kullanıcı adı zaten kullanılıyor.");
            }
            user.setUsername(requestedUsername);
            usernameChanged = true;
        }

        boolean roleChanged = user.getRole() != form.getRole();
        boolean enabledChanged = user.isEnabled() != form.isEnabled();
        user.setFullName(form.getFullName().trim());
        user.setRole(form.getRole());
        user.setEnabled(form.isEnabled());
        User saved = userRepository.save(user);
        if (usernameChanged) {
            sessionRevocationService.revokeAllSessions(oldUsername);
        } else if (roleChanged || enabledChanged) {
            sessionRevocationService.revokeAllSessions(saved.getUsername());
        }
        auditService.record(AuditAction.USER_UPDATED, "Kullanıcı", saved.getId(),
                "Kullanıcı güncellendi: " + (usernameChanged ? oldUsername + " -> " + saved.getUsername()
                        : saved.getUsername()) + ", rol=" + saved.getRole()
                        + ", aktif=" + saved.isEnabled());
        return saved;
    }

    /**
     * PATRON tarafından kullanıcıya tek kullanımlık, 24 saat geçerli kurtarma kodu üretir.
     * Kod yalnızca bir kez gösterilir; veritabanında BCrypt ile saklanır.
     */
    @Transactional
    public String generateRecoveryCode(Long userId) {
        if (!currentUserService.isPatron()) {
            throw new BusinessRuleException("Kurtarma kodu oluşturma yetkiniz yok.");
        }
        User user = findById(userId);
        String code = randomRecoveryCode();
        LocalDateTime now = LocalDateTime.now(clock);
        user.setRecoveryCodeHash(passwordEncoder.encode(code));
        user.setRecoveryCodeCreatedAt(now);
        user.setRecoveryCodeExpiresAt(now.plus(RECOVERY_CODE_TTL));
        userRepository.save(user);
        auditService.record(AuditAction.USER_UPDATED, "Kullanıcı", user.getId(),
                "Kurtarma kodu oluşturuldu: " + user.getUsername());
        return code;
    }

    /**
     * Giriş ekranındaki "şifremi unuttum" akışı. Kod tek kullanımlıktır ve süresi dolunca geçersizdir.
     */
    @Transactional
    public void resetPasswordWithRecoveryCode(String username, String code, String newPassword) {
        PasswordPolicy.requireStrong(newPassword);
        User user = userRepository.findByUsernameIgnoreCase(normalizeUsername(username)).orElse(null);
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String storedHash = user == null || user.getRecoveryCodeHash() == null
                ? DUMMY_RECOVERY_HASH : user.getRecoveryCodeHash();
        boolean codeMatches = !normalizedCode.isBlank()
                && passwordEncoder.matches(normalizedCode, storedHash);
        boolean valid = user != null && user.isEnabled()
                && user.getRecoveryCodeHash() != null
                && user.getRecoveryCodeExpiresAt() != null
                && user.getRecoveryCodeExpiresAt().isAfter(LocalDateTime.now(clock))
                && codeMatches;
        if (!valid) {
            throw new BusinessRuleException(
                    "Kullanıcı adı veya kurtarma kodu hatalı ya da kodun süresi dolmuş.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setRecoveryCodeHash(null);
        user.setRecoveryCodeExpiresAt(null);
        user.setRecoveryCodeCreatedAt(null);
        userRepository.save(user);
        sessionRevocationService.revokeAllSessions(user.getUsername());
        auditService.recordAs(user.getId(), user.getUsername(), AuditAction.USER_PASSWORD_RESET,
                "Kullanıcı", user.getId(), "Kurtarma koduyla parola sıfırlandı");
    }

    /**
     * Kullanıcının kendi kullanıcı adını değiştirmesi. Mevcut parola doğrulanır ve
     * tüm oturumlar sonlandırılır.
     */
    @Transactional
    public void changeOwnUsername(Long id, String newUsername, String currentPassword) {
        User user = findById(id);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Mevcut parola hatalı.");
        }
        String normalized = normalizeUsername(newUsername);
        if (normalized.equals(user.getUsername())) {
            throw new BusinessRuleException("Yeni kullanıcı adı mevcut kullanıcı adıyla aynı.");
        }
        validateUsername(normalized);
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalized, id)) {
            throw new BusinessRuleException("Bu kullanıcı adı zaten kullanılıyor.");
        }
        String oldUsername = user.getUsername();
        user.setUsername(normalized);
        userRepository.save(user);
        sessionRevocationService.revokeAllSessions(oldUsername);
        auditService.record(AuditAction.USER_UPDATED, "Kullanıcı", user.getId(),
                "Kullanıcı adı değiştirildi: " + oldUsername + " -> " + normalized);
    }

    @Transactional
    public void changePassword(Long id, String rawPassword) {
        User user = findById(id);
        PasswordPolicy.requireStrong(rawPassword);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        sessionRevocationService.revokeAllSessions(user.getUsername());
        auditService.record(AuditAction.USER_PASSWORD_RESET, "Kullanıcı", user.getId(),
                "Parola yönetici tarafından sıfırlandı: " + user.getUsername());
    }

    @Transactional
    public void changeOwnPassword(Long id, String currentPassword, String newPassword) {
        User user = findById(id);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Mevcut parola hatalı.");
        }
        PasswordPolicy.requireStrong(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditService.record(AuditAction.PASSWORD_CHANGED, "Kullanıcı", user.getId(),
                "Kullanıcı kendi parolasını değiştirdi: " + user.getUsername());
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void validateUsername(String username) {
        if (username == null || !username.matches("^[a-z0-9._-]{3,50}$")) {
            throw new BusinessRuleException(
                    "Kullanıcı adı 3-50 karakter olmalı; harf, rakam, nokta, tire ve alt çizgi içerebilir.");
        }
    }

    private String randomRecoveryCode() {
        StringBuilder code = new StringBuilder(RECOVERY_CODE_LENGTH);
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            code.append(RECOVERY_ALPHABET[SECURE_RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return code.toString();
    }
}
