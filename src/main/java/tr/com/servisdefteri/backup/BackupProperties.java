package tr.com.servisdefteri.backup;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.backup")
@Getter
@Setter
public class BackupProperties {

    private boolean enabled = true;
    private String directory = "./backups";
    private int retentionDays = 14;
    /** Saklama süresi dolsa bile en yeni kaç yedeğin silinmeyeceği. */
    private int minCopies = 5;
    private String cron = "0 0 2 * * *";
    private String pgDumpPath = "pg_dump";
    private String pgRestorePath = "pg_restore";
    /** pg_dump işleminin izin verilen en uzun süresi (saniye). */
    private int timeoutSeconds = 300;
    /** Yedek dosyasının pg_restore --list ile doğrulanıp doğrulanmayacağı. */
    private boolean verify = true;
    /** Başarılı yedekten sonra ikinci kopyanın yazılacağı klasör (harici disk/NAS). */
    private String copyDirectory = "";
    private String host = "localhost";
    private int port = 5432;
    private String database = "servisdefteri";
    private String username = "servisdefteri";
    private String password = "";
}
