package tr.com.servisdefteri.common;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Dosya indirme yanitlari icin ortak basliklar.
 */
public final class DownloadUtil {

    public static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    public static final MediaType PDF = MediaType.APPLICATION_PDF;

    private DownloadUtil() {
    }

    public static ResponseEntity<byte[]> csv(byte[] content, String fileName) {
        return download(content, fileName, new MediaType("text", "csv", StandardCharsets.UTF_8));
    }

    public static ResponseEntity<byte[]> xlsx(byte[] content, String fileName) {
        return download(content, fileName, XLSX);
    }

    public static ResponseEntity<byte[]> pdf(byte[] content, String fileName) {
        return download(content, fileName, PDF);
    }

    private static ResponseEntity<byte[]> download(byte[] content, String fileName, MediaType mediaType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(mediaType)
                .body(content);
    }
}
