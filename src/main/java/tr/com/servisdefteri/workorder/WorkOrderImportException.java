package tr.com.servisdefteri.workorder;

import java.util.List;

/**
 * Toplu iş emri içe aktarma sırasında oluşan satır hatalarını taşır.
 */
public class WorkOrderImportException extends RuntimeException {

    private final List<ImportError> errors;

    public WorkOrderImportException(List<ImportError> errors) {
        super("İçe aktarma doğrulanamadı.");
        this.errors = List.copyOf(errors);
    }

    public List<ImportError> getErrors() {
        return errors;
    }

    public record ImportError(int row, String message) {
    }
}
