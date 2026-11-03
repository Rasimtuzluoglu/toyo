package tr.com.servisdefteri.common;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Thymeleaf sablonlarinda Turkce para, sayi ve tarih gosterimi icin kullanilir.
 */
@Component("fmt")
public class TemplateFormat {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", TR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", TR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", TR);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd MMM", TR);
    private static final DateTimeFormatter DAY_NAME = DateTimeFormatter.ofPattern("EEEE", TR);

    public String money(BigDecimal value) {
        if (value == null) {
            return "0,00 ₺";
        }
        return decimal(value, "#,##0.00") + " ₺";
    }

    public String number(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return decimal(value, "#,##0.##");
    }

    public String number(Integer value) {
        return value == null ? "0" : decimal(BigDecimal.valueOf(value), "#,##0");
    }

    public String number(Long value) {
        return value == null ? "0" : decimal(BigDecimal.valueOf(value), "#,##0");
    }

    public String percent(BigDecimal value) {
        if (value == null) {
            return "%0";
        }
        return decimal(value, "#,##0.0") + " %";
    }

    public String date(LocalDate value) {
        return value == null ? "—" : DATE.format(value);
    }

    public String dateTime(LocalDateTime value) {
        return value == null ? "—" : DATE_TIME.format(value);
    }

    public String time(LocalDateTime value) {
        return value == null ? "—" : TIME.format(value);
    }

    public String dayMonth(LocalDate value) {
        return value == null ? "—" : DAY_MONTH.format(value);
    }

    public String dayName(LocalDate value) {
        return value == null ? "—" : DAY_NAME.format(value);
    }

    public String empty(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public String fileSize(Long bytes) {
        if (bytes == null) {
            return "—";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return decimal(BigDecimal.valueOf(bytes / 1024.0), "#,##0.0") + " KB";
        }
        return decimal(BigDecimal.valueOf(bytes / (1024.0 * 1024.0)), "#,##0.0") + " MB";
    }

    private String decimal(BigDecimal value, String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(TR);
        DecimalFormat format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }
}
