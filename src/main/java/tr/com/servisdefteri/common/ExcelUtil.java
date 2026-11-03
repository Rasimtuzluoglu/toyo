package tr.com.servisdefteri.common;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Apache POI ile tek sayfalik, bicimlendirilmis .xlsx ciktisi uretir.
 */
public final class ExcelUtil {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String MONEY_FORMAT = "#,##0.00 \"₺\"";
    private static final String NUMBER_FORMAT = "#,##0.##";
    private static final int MAX_COLUMN_WIDTH = 55;
    private static final int MIN_COLUMN_WIDTH = 10;

    private ExcelUtil() {
    }

    public static byte[] toXlsx(String sheetName, String title, List<String> infoLines,
                                List<String> headers, List<List<Object>> rows) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("Excel ciktisi icin en az bir sutun basligi gerekir.");
        }
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));

            Styles styles = new Styles(workbook);
            int rowIndex = 0;

            if (title != null && !title.isBlank()) {
                Row titleRow = sheet.createRow(rowIndex++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(title);
                titleCell.setCellStyle(styles.title);
                sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(),
                        0, headers.size() - 1));
                titleRow.setHeightInPoints(24);
            }

            if (infoLines != null) {
                for (String line : infoLines) {
                    if (line == null || line.isBlank()) {
                        rowIndex++;
                        continue;
                    }
                    Row infoRow = sheet.createRow(rowIndex++);
                    Cell infoCell = infoRow.createCell(0);
                    infoCell.setCellValue(line);
                    infoCell.setCellStyle(styles.info);
                    sheet.addMergedRegion(new CellRangeAddress(infoRow.getRowNum(), infoRow.getRowNum(),
                            0, headers.size() - 1));
                }
            }

            if (title != null || (infoLines != null && !infoLines.isEmpty())) {
                rowIndex++;
            }

            int headerRowIndex = rowIndex;
            Row headerRow = sheet.createRow(headerRowIndex);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.header);
            }

            int[] widths = new int[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                widths[i] = headers.get(i) == null ? MIN_COLUMN_WIDTH : headers.get(i).length();
            }

            int lastRowIndex = headerRowIndex;
            if (rows != null) {
                for (List<Object> values : rows) {
                    Row row = sheet.createRow(++lastRowIndex);
                    for (int i = 0; i < headers.size(); i++) {
                        Object value = values != null && i < values.size() ? values.get(i) : null;
                        Cell cell = row.createCell(i);
                        writeValue(cell, value, styles);
                        widths[i] = Math.max(widths[i], displayLength(value));
                    }
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                int width = Math.min(Math.max(widths[i] + 2, MIN_COLUMN_WIDTH), MAX_COLUMN_WIDTH);
                sheet.setColumnWidth(i, width * 256);
            }

            if (lastRowIndex > headerRowIndex) {
                sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, lastRowIndex, 0, headers.size() - 1));
            }
            sheet.createFreezePane(0, headerRowIndex + 1);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Excel dosyası oluşturulamadı.", ex);
        }
    }

    private static void writeValue(Cell cell, Object value, Styles styles) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof BigDecimal money) {
            cell.setCellValue(money.doubleValue());
            cell.setCellStyle(styles.money);
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(styles.number);
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(toDate(date));
            cell.setCellStyle(styles.date);
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(toDate(dateTime.toLocalDate()));
            cell.setCellStyle(styles.dateTime);
            return;
        }
        if (value instanceof Boolean flag) {
            cell.setCellValue(flag ? "Evet" : "Hayır");
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            cell.setCellValue(enumValue.toString());
            return;
        }
        cell.setCellValue(value.toString());
    }

    private static Date toDate(LocalDate value) {
        return Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static int displayLength(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof BigDecimal money) {
            return CsvUtil.money(money).length() + 3;
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMAT.format(date).length();
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_FORMAT.format(dateTime).length();
        }
        return value.toString().length();
    }

    private static String safeSheetName(String sheetName) {
        String name = sheetName == null || sheetName.isBlank() ? "Rapor" : sheetName;
        name = name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return name.length() > 31 ? name.substring(0, 31) : name;
    }

    private static final class Styles {

        private final CellStyle title;
        private final CellStyle info;
        private final CellStyle header;
        private final CellStyle money;
        private final CellStyle number;
        private final CellStyle date;
        private final CellStyle dateTime;

        private Styles(Workbook workbook) {
            var titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            var infoFont = workbook.createFont();
            infoFont.setFontHeightInPoints((short) 10);
            infoFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            info = workbook.createCellStyle();
            info.setFont(infoFont);

            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.LEFT);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);

            var bodyBorder = BorderStyle.THIN;
            money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat(MONEY_FORMAT));
            money.setBorderBottom(bodyBorder);

            number = workbook.createCellStyle();
            number.setDataFormat(workbook.createDataFormat().getFormat(NUMBER_FORMAT));
            number.setBorderBottom(bodyBorder);

            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd.MM.yyyy"));
            date.setBorderBottom(bodyBorder);

            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.createDataFormat().getFormat("dd.MM.yyyy HH:mm"));
            dateTime.setBorderBottom(bodyBorder);
        }
    }
}
