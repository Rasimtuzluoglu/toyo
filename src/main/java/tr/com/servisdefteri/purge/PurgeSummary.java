package tr.com.servisdefteri.purge;

import java.util.Map;

/**
 * Kalıcı silme onay ekranında gösterilecek özet.
 */
public record PurgeSummary(String kind,
                           Long id,
                           String reference,
                           String title,
                           String warning,
                           Map<String, String> counts) {
}
