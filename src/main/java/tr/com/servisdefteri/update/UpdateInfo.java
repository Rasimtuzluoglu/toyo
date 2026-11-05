package tr.com.servisdefteri.update;

import lombok.Getter;
import lombok.Setter;

/**
 * Ayarlar ekranındaki yazılım güncelleme kartının görünüm modeli.
 */
@Getter
@Setter
public class UpdateInfo {

    private boolean enabled;
    private String currentCommit;
    private String currentCommitLabel;
    private String latestCommit;
    private String latestCommitLabel;
    private String latestMessage;
    private String latestDate;
    private boolean updateAvailable;
    private String checkError;

    private boolean agentOnline;
    private boolean autoUpdate;
    private String agentHeartbeat;

    private String statusState;
    private String statusLabel;
    private String statusMessage;
    private String statusCommit;
    private String statusStartedAt;
    private String statusFinishedAt;
}
