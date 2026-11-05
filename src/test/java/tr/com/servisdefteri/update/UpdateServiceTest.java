package tr.com.servisdefteri.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UpdateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T20:00:00Z");

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void infoReadsAgentHeartbeatAndLastUpdateStatus() throws Exception {
        Files.writeString(tempDir.resolve("deployed-commit"), "abc1234");
        Files.writeString(tempDir.resolve("agent.status"),
                "{\"heartbeat\":\"2026-11-05T20:00:00Z\",\"auto\":true}");
        Files.writeString(tempDir.resolve("update.status"),
                "{\"state\":\"success\",\"message\":\"Güncelleme tamamlandı (abc1234).\","
                        + "\"commit\":\"abc1234\",\"startedAt\":\"2026-11-05T19:55:00Z\","
                        + "\"finishedAt\":\"2026-11-05T19:57:00Z\"}");

        UpdateInfo info = service("unknown").info();

        assertThat(info.isEnabled()).isTrue();
        assertThat(info.getCurrentCommitLabel()).isEqualTo("abc1234");
        assertThat(info.isAgentOnline()).isTrue();
        assertThat(info.isAutoUpdate()).isTrue();
        assertThat(info.getStatusLabel()).isEqualTo("Tamamlandı");
        assertThat(info.getStatusMessage()).contains("Güncelleme tamamlandı");
    }

    @Test
    void requestUpdateStopsWhenAgentIsOffline() {
        assertThatThrownBy(() -> service("abc1234").requestUpdate("patron"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Güncelleme ajanı çalışmıyor");
    }

    @Test
    void unknownCurrentCommitIsShownAsUnknown() {
        UpdateInfo info = service("unknown").info();

        assertThat(info.getCurrentCommitLabel()).isEqualTo("bilinmiyor");
        assertThat(info.isAgentOnline()).isFalse();
    }

    private UpdateService service(String configuredCommit) {
        return new UpdateService(true, tempDir.toString(), "owner/repo", configuredCommit, "",
                objectMapper, Clock.fixed(NOW, ZoneId.of("Europe/Istanbul")),
                mock(AuditService.class));
    }
}
