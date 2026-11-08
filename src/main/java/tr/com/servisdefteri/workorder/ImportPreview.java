package tr.com.servisdefteri.workorder;

import java.util.List;

/**
 * İçe aktarma öncesi dosyanın kaydedilmeden gösterilecek özeti.
 */
public record ImportPreview(String fileName, int totalRows, List<ImportRow> rows) {

    public record ImportRow(int rowNo,
                            String orderNo,
                            String plate,
                            String customerName,
                            String phone,
                            String complaint,
                            String serviceDescription,
                            String assignedName,
                            Integer mileage) {
    }
}
