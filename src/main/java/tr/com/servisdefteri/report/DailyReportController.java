package tr.com.servisdefteri.report;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.pdf.PdfService;

import java.time.Clock;
import java.time.LocalDate;

@Controller
@RequestMapping("/daily-report")
@PreAuthorize("hasAnyRole('PATRON','MUHASEBE','SERVIS')")
public class DailyReportController {

    private final DailyReportService dailyReportService;
    private final PdfService pdfService;
    private final Clock clock;

    public DailyReportController(DailyReportService dailyReportService, PdfService pdfService, Clock clock) {
        this.dailyReportService = dailyReportService;
        this.pdfService = pdfService;
        this.clock = clock;
    }

    @GetMapping
    public String index(@RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        Model model) {
        LocalDate selected = selectedDate(date);
        model.addAttribute("report", dailyReportService.load(selected));
        model.addAttribute("selectedDate", selected);
        model.addAttribute("activeMenu", "daily-report");
        return "daily-report/index";
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selected = selectedDate(date);
        DailyReportView report = dailyReportService.load(selected);
        return DownloadUtil.xlsx(dailyReportService.excel(report),
                "gunluk-isler-" + selected + ".xlsx");
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selected = selectedDate(date);
        DailyReportView report = dailyReportService.load(selected);
        return DownloadUtil.pdf(pdfService.dailyReportPdf(report),
                "gunluk-isler-" + selected + ".pdf");
    }

    private LocalDate selectedDate(LocalDate date) {
        return date == null ? LocalDate.now(clock) : date;
    }
}
