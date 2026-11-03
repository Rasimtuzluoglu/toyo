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
    private String cron = "0 0 2 * * *";
    private String pgDumpPath = "pg_dump";
    private String host = "localhost";
    private int port = 5432;
    private String database = "servisdefteri";
    private String username = "servisdefteri";
    private String password = "";
}
