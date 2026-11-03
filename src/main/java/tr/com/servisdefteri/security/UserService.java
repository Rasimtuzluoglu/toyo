package tr.com.servisdefteri.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuditService auditService, CurrentUserService currentUserService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return userRepository.count() > 0;
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
        if (userRepository.count() > 0) {
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

        user.setFullName(form.getFullName().trim());
        user.setRole(form.getRole());
        user.setEnabled(form.isEnabled());
        User saved = userRepository.save(user);
        auditService.record(AuditAction.USER_UPDATED, "Kullanıcı", saved.getId(),
                "Kullanıcı güncellendi: " + saved.getUsername() + ", rol=" + saved.getRole()
                        + ", aktif=" + saved.isEnabled());
        return saved;
    }

    @Transactional
    public void changePassword(Long id, String rawPassword) {
        User user = findById(id);
        PasswordPolicy.requireStrong(rawPassword);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
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
}
