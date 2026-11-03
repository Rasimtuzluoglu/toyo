package tr.com.servisdefteri.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyCollection(LocalDate date, BigDecimal amount, int percent) {
}
