package tr.com.servisdefteri.workorder;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.vehicle.VehicleService;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CSV veya Excel dosyasından toplu iş emri oluşturur.
 * Tüm satırlar doğrulanmadan hiçbir kayıt oluşturulmaz.
 */
@Service
public class WorkOrderImportService {

    private static final int MAX_ROWS = 200;

    private final QuickWorkOrderService quickWorkOrderService;
    private final WorkOrderRepository workOrderRepository;
    private final VehicleService vehicleService;

    public WorkOrderImportService(QuickWorkOrderService quickWorkOrderService,
                                  WorkOrderRepository workOrderRepository,
                                  VehicleService vehicleService) {
        this.quickWorkOrderService = quickWorkOrderService;
        this.workOrderRepository = workOrderRepository;
        this.vehicleService = vehicleService;
    }

    @Transactional
    public int importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new WorkOrderImportException(List.of(
                    new WorkOrderImportException.ImportError(0, "Dosya seçilmelidir.")));
        }
        List<List<String>> rows = readRows(file);
        if (rows.isEmpty()) {
            throw new WorkOrderImportException(List.of(
                    new WorkOrderImportException.ImportError(0, "Dosyada veri satırı bulunamadı.")));
        }
        if (rows.size() > MAX_ROWS + 1) {
            throw new WorkOrderImportException(List.of(
                    new WorkOrderImportException.ImportError(0,
                            "Tek seferde en fazla " + MAX_ROWS + " iş emri aktarılabilir.")));
        }

        Map<String, Integer> columns = headerIndexes(rows.get(0));
        List<WorkOrderImportException.ImportError> errors = new ArrayList<>();
        validateRequiredHeaders(columns, errors);
        if (!errors.isEmpty()) {
            throw new WorkOrderImportException(errors);
        }

        List<QuickWorkOrderForm> forms = new ArrayList<>();
        List<Integer> formRows = new ArrayList<>();
        Set<String> seenOrderNos = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int rowNo = i + 1;
            QuickWorkOrderForm form = toForm(row, columns, rowNo, seenOrderNos, errors);
            if (form != null) {
                forms.add(form);
                formRows.add(rowNo);
            }
        }
        if (!errors.isEmpty()) {
            throw new WorkOrderImportException(errors);
        }

        for (int i = 0; i < forms.size(); i++) {
            try {
                quickWorkOrderService.create(forms.get(i));
            } catch (BusinessRuleException ex) {
                throw new WorkOrderImportException(List.of(
                        new WorkOrderImportException.ImportError(formRows.get(i), ex.getMessage())));
            }
        }
        return forms.size();
    }

    public byte[] template() {
        String csv = "\uFEFFFiş No;Plaka;Müşteri;Telefon;Şikâyet;Yapılacak İş;İlgilenen;Kilometre\r\n"
                + "IS-2026-000013;34 ABC 123;Ahmet Yılmaz;0532 000 00 00;"
                + "Fren sesi;Fren bakımı;Ahmet Usta;62000\r\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private QuickWorkOrderForm toForm(List<String> row, Map<String, Integer> columns, int rowNo,
                                      Set<String> seenOrderNos,
                                      List<WorkOrderImportException.ImportError> errors) {
        String orderNo = value(row, columns.get("orderNo"));
        String plate = value(row, columns.get("plate"));
        String customerName = value(row, columns.get("customerName"));
        String phone = value(row, columns.get("phone"));
        String complaint = value(row, columns.get("complaint"));
        String serviceDescription = value(row, columns.get("serviceDescription"));
        String assignedName = value(row, columns.get("assignedName"));
        String mileageText = value(row, columns.get("mileage"));

        boolean valid = true;
        if (orderNo.isBlank()) {
            errors.add(new WorkOrderImportException.ImportError(rowNo, "Fiş no zorunludur."));
            valid = false;
        } else if (orderNo.length() > 30) {
            errors.add(new WorkOrderImportException.ImportError(rowNo,
                    "Fiş no en fazla 30 karakter olabilir."));
            valid = false;
        } else if (!seenOrderNos.add(orderNo.toLowerCase(Locale.ROOT))) {
            errors.add(new WorkOrderImportException.ImportError(rowNo,
                    "Fiş no dosyada tekrar ediyor: " + orderNo));
            valid = false;
        } else if (workOrderRepository.existsByOrderNoIgnoreCase(orderNo)) {
            errors.add(new WorkOrderImportException.ImportError(rowNo,
                    "Fiş no zaten kullanılıyor: " + orderNo));
            valid = false;
        }
        if (plate.isBlank()) {
            errors.add(new WorkOrderImportException.ImportError(rowNo, "Plaka zorunludur."));
            valid = false;
        }
        if (complaint.isBlank()) {
            errors.add(new WorkOrderImportException.ImportError(rowNo, "Şikâyet zorunludur."));
            valid = false;
        }
        if (serviceDescription.isBlank()) {
            errors.add(new WorkOrderImportException.ImportError(rowNo, "Yapılacak iş zorunludur."));
            valid = false;
        }
        if (assignedName.length() > 100) {
            errors.add(new WorkOrderImportException.ImportError(rowNo,
                    "İlgilenen kişi en fazla 100 karakter olabilir."));
            valid = false;
        }
        if (!plate.isBlank() && vehicleService.findByPlate(plate) == null) {
            if (customerName.isBlank()) {
                errors.add(new WorkOrderImportException.ImportError(rowNo,
                        "Yeni plaka için müşteri adı zorunludur."));
                valid = false;
            }
            if (phone.isBlank()) {
                errors.add(new WorkOrderImportException.ImportError(rowNo,
                        "Yeni plaka için telefon zorunludur."));
                valid = false;
            }
        }

        Integer mileage = null;
        if (!mileageText.isBlank()) {
            try {
                mileage = Integer.parseInt(mileageText.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ex) {
                errors.add(new WorkOrderImportException.ImportError(rowNo,
                        "Kilometre sayı olmalıdır."));
                valid = false;
            }
        }
        if (!valid) {
            return null;
        }

        QuickWorkOrderForm form = new QuickWorkOrderForm();
        form.setOrderNo(orderNo);
        form.setPlate(plate);
        form.setCustomerName(customerName);
        form.setPhone(phone);
        form.setComplaint(complaint);
        form.setServiceDescription(serviceDescription);
        form.setAssignedName(assignedName);
        form.setMileage(mileage);
        form.setCompleteNow(false);
        return form;
    }

    private Map<String, Integer> headerIndexes(List<String> headerRow) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String header = normalizeHeader(headerRow.get(i));
            if (header.isEmpty()) {
                continue;
            }
            if (isAny(header, "fisno", "isemrino", "orderno", "fisnumarasi")) {
                columns.putIfAbsent("orderNo", i);
            } else if (isAny(header, "plaka", "plate")) {
                columns.putIfAbsent("plate", i);
            } else if (isAny(header, "musteri", "musteriadi", "customer", "adsoyad")) {
                columns.putIfAbsent("customerName", i);
            } else if (isAny(header, "telefon", "phone", "telefonno")) {
                columns.putIfAbsent("phone", i);
            } else if (isAny(header, "sikayet", "sikayeti", "complaint")) {
                columns.putIfAbsent("complaint", i);
            } else if (isAny(header, "yapilacakis", "yapilacak", "islem", "service",
                    "servisaciklamasi")) {
                columns.putIfAbsent("serviceDescription", i);
            } else if (isAny(header, "ilgilenen", "sorumlu", "assigned")) {
                columns.putIfAbsent("assignedName", i);
            } else if (isAny(header, "kilometre", "km", "mileage")) {
                columns.putIfAbsent("mileage", i);
            }
        }
        return columns;
    }

    private void validateRequiredHeaders(Map<String, Integer> columns,
                                         List<WorkOrderImportException.ImportError> errors) {
        if (!columns.containsKey("orderNo")) {
            errors.add(new WorkOrderImportException.ImportError(1, "Fiş No sütunu bulunamadı."));
        }
        if (!columns.containsKey("plate")) {
            errors.add(new WorkOrderImportException.ImportError(1, "Plaka sütunu bulunamadı."));
        }
        if (!columns.containsKey("complaint")) {
            errors.add(new WorkOrderImportException.ImportError(1, "Şikâyet sütunu bulunamadı."));
        }
        if (!columns.containsKey("serviceDescription")) {
            errors.add(new WorkOrderImportException.ImportError(1,
                    "Yapılacak İş sütunu bulunamadı."));
        }
    }

    private List<List<String>> readRows(MultipartFile file) {
        String name = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readExcel(file);
            }
            if (name.endsWith(".csv")) {
                return readCsv(file.getBytes());
            }
        } catch (WorkOrderImportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WorkOrderImportException(List.of(
                    new WorkOrderImportException.ImportError(0,
                            "Dosya okunamadı. CSV veya Excel dosyası olduğundan emin olun.")));
        }
        throw new WorkOrderImportException(List.of(
                new WorkOrderImportException.ImportError(0,
                        "Yalnızca .csv, .xlsx veya .xls dosyaları desteklenir.")));
    }

    private List<List<String>> readExcel(MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<List<String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                int last = Math.max(row.getLastCellNum(), 0);
                for (int i = 0; i < last; i++) {
                    values.add(formatter.formatCellValue(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
                }
                if (!isBlankRow(values)) {
                    rows.add(values);
                }
            }
            return rows;
        }
    }

    private List<List<String>> readCsv(byte[] bytes) {
        String text = decodeCsv(bytes);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (!isBlankRow(row)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            if (!isBlankRow(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private String decodeCsv(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            return new String(bytes, Charset.forName("windows-1254"));
        }
    }

    private char detectDelimiter(String text) {
        String firstLine = text.split("\\R", 2)[0];
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long commas = firstLine.chars().filter(c -> c == ',').count();
        return semicolons >= commas ? ';' : ',';
    }

    private boolean isBlankRow(List<String> row) {
        for (String value : row) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String value(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replace('â', 'a')
                .replace('î', 'i')
                .replace('û', 'u')
                .replaceAll("[^a-z0-9]+", "");
    }

    private boolean isAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
