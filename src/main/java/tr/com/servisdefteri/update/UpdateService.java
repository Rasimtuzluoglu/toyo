package tr.com.servisdefteri.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitHub'daki son commit'i kontrol eder ve host tarafındaki güncelleme ajanına
 * dosya tabanlı bir güncelleme isteği bırakır.
 *
 * <p>Uygulama container içinde çalıştığı için Docker komutu çalıştırmaz;
 * gerçek git pull + docker compose işlemini host'taki
 * {@code scripts/update-agent.ps1} yapar.</p>
 */
@Service
public class UpdateService {

    private static final Duration AGENT_ONLINE_WINDOW = Duration.ofMinutes(5);
    private static final DateTimeFormatter GITHUB_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final boolean enabled;
    private final Path directory;
    private final String repository;
    private final String configuredCommit;
    private final String githubToken;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AuditService auditService;
    private final HttpClient httpClient;

    private volatile LatestCommit latest;
    private volatile String lastError;

    public UpdateService(@Value("${app.update.enabled:true}") boolean enabled,
                         @Value("${app.update.directory:./updates}") String directory,
                         @Value("${app.update.repository:Rasimtuzluoglu/toyo}") String repository,
                         @Value("${app.update.current-commit:unknown}") String configuredCommit,
                         @Value("${app.update.github-token:}") String githubToken,
                         ObjectMapper objectMapper,
                         Clock clock,
                         AuditService auditService) {
        this.enabled = enabled;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.repository = repository;
        this.configuredCommit = configuredCommit;
        this.githubToken = githubToken;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.auditService = auditService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public UpdateInfo info() {
        UpdateInfo info = new UpdateInfo();
        info.setEnabled(enabled);

        String current = currentCommit();
        info.setCurrentCommit(current);
        info.setCurrentCommitLabel(shortCommit(current));

        LatestCommit cached = latest;
        if (cached != null) {
            info.setLatestCommit(cached.sha());
            info.setLatestCommitLabel(shortCommit(cached.sha()));
            info.setLatestMessage(cached.message());
            info.setLatestDate(formatDate(cached.date()));
            info.setUpdateAvailable(!sameCommit(current, cached.sha()));
        }

        AgentState agent = readAgentState();
        info.setAgentOnline(agent != null && agent.online());
        info.setAutoUpdate(agent != null && agent.autoUpdate());
        info.setAgentHeartbeat(agent == null ? null : formatDate(agent.heartbeat()));
        info.setCheckError(lastError);

        RunState run = readRunState();
        if (run == null) {
            info.setStatusState("idle");
            info.setStatusLabel("Henüz güncelleme çalıştırılmadı");
        } else {
            info.setStatusState(run.state());
            info.setStatusLabel(statusLabel(run.state()));
            info.setStatusMessage(run.message());
            info.setStatusCommit(shortCommit(run.commit()));
            info.setStatusStartedAt(formatDate(run.startedAt()));
            info.setStatusFinishedAt(formatDate(run.finishedAt()));
        }
        return info;
    }

    public void checkForUpdates() {
        requireEnabled();
        try {
            LatestCommit fetched = fetchLatestCommit();
            latest = fetched;
            lastError = null;
        } catch (RuntimeException ex) {
            lastError = "GitHub kontrolü başarısız: " + ex.getMessage();
            throw new BusinessRuleException(lastError);
        }
    }

    public void requestUpdate(String requestedBy) {
        requireEnabled();
        UpdateInfo info = info();
        if (!info.isAgentOnline()) {
            throw new BusinessRuleException("Güncelleme ajanı çalışmıyor. "
                    + "Sunucuda scripts/install-update-agent.ps1 betiğini çalıştırın.");
        }
        checkForUpdates();
        LatestCommit target = latest;
        if (target == null) {
            throw new BusinessRuleException("Güncelleme hedefi alınamadı.");
        }
        if (sameCommit(currentCommit(), target.sha())) {
            throw new BusinessRuleException("Sistem zaten güncel (" + shortCommit(target.sha()) + ").");
        }
        writeRequest(target.sha(), requestedBy);
        auditService.record(AuditAction.APP_UPDATE_REQUESTED, "Sistem", null,
                "Yazılım güncellemesi istendi: " + shortCommit(target.sha()));
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessRuleException("Yazılım güncelleme özelliği kapalı.");
        }
    }

    private LatestCommit fetchLatestCommit() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create("https://api.github.com/repos/" + repository + "/commits/main"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ServisDefteri-Updater")
                    .GET();
            if (githubToken != null && !githubToken.isBlank()) {
                builder.header("Authorization", "Bearer " + githubToken.trim());
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("GitHub HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String sha = root.path("sha").asText("");
            if (sha.isBlank()) {
                throw new IllegalStateException("Commit bilgisi okunamadı");
            }
            String message = root.path("commit").path("message").asText("");
            String date = root.path("commit").path("committer").path("date").asText("");
            return new LatestCommit(sha, firstLine(message), date);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("İstek kesildi");
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException(ex.getMessage() == null ? "bağlantı hatası" : ex.getMessage());
        }
    }

    private void writeRequest(String targetCommit, String requestedBy) {
        try {
            Files.createDirectories(directory);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("targetCommit", targetCommit);
            request.put("requestedAt", Instant.now(clock).toString());
            request.put("requestedBy", requestedBy);
            Path temp = directory.resolve("update.request.tmp");
            Path target = directory.resolve("update.request");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), request);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("Güncelleme isteği yazılamadı: " + ex.getMessage());
        }
    }

    private AgentState readAgentState() {
        JsonNode root = readJson(directory.resolve("agent.status"));
        if (root == null) {
            return null;
        }
        String heartbeat = root.path("heartbeat").asText(null);
        boolean online = false;
        if (heartbeat != null && !heartbeat.isBlank()) {
            try {
                Instant beat = OffsetDateTime.parse(heartbeat).toInstant();
                online = !beat.isBefore(Instant.now(clock).minus(AGENT_ONLINE_WINDOW));
            } catch (RuntimeException ignored) {
                // Geçersiz heartbeat çevrimdışı sayılır.
            }
        }
        return new AgentState(online, root.path("auto").asBoolean(false), heartbeat);
    }

    private RunState readRunState() {
        JsonNode root = readJson(directory.resolve("update.status"));
        if (root == null) {
            return null;
        }
        return new RunState(root.path("state").asText("idle"),
                root.path("message").asText(""),
                root.path("commit").asText(""),
                root.path("startedAt").asText(""),
                root.path("finishedAt").asText(""));
    }

    private JsonNode readJson(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return objectMapper.readTree(file.toFile());
        } catch (IOException ex) {
            return null;
        }
    }

    private String currentCommit() {
        if (configuredCommit != null && !configuredCommit.isBlank()
                && !"unknown".equalsIgnoreCase(configuredCommit.trim())) {
            return configuredCommit.trim();
        }
        try {
            Path deployed = directory.resolve("deployed-commit");
            if (Files.isRegularFile(deployed)) {
                String value = Files.readString(deployed).trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        } catch (IOException ignored) {
            // Dosya okunamazsa bilinmiyor olarak gösterilir.
        }
        return "unknown";
    }

    private String statusLabel(String state) {
        return switch (state == null ? "" : state) {
            case "queued" -> "Sırada";
            case "running" -> "Sürüyor";
            case "success" -> "Tamamlandı";
            case "error" -> "Hata";
            default -> "Bilgi yok";
        };
    }

    private boolean sameCommit(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        String a = first.trim();
        String b = second.trim();
        if (a.isEmpty() || b.isEmpty() || "unknown".equalsIgnoreCase(a)) {
            return false;
        }
        return a.equalsIgnoreCase(b)
                || (a.length() >= 7 && b.startsWith(a))
                || (b.length() >= 7 && a.startsWith(b));
    }

    private String shortCommit(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            return "bilinmiyor";
        }
        String trimmed = value.trim();
        return trimmed.length() > 7 ? trimmed.substring(0, 7) : trimmed;
    }

    private String formatDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(clock.getZone())
                    .format(GITHUB_DATE);
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int lineBreak = message.indexOf('\n');
        return lineBreak < 0 ? message.trim() : message.substring(0, lineBreak).trim();
    }

    private record LatestCommit(String sha, String message, String date) {
    }

    private record AgentState(boolean online, boolean autoUpdate, String heartbeat) {
    }

    private record RunState(String state, String message, String commit,
                            String startedAt, String finishedAt) {
    }
}
