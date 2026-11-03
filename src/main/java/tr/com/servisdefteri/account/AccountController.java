package tr.com.servisdefteri.account;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.CsvUtil;
import tr.com.servisdefteri.common.DownloadUtil;
import tr.com.servisdefteri.common.SearchNormalizer;
import tr.com.servisdefteri.common.FormTokenService;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.report.ReportService;
import tr.com.servisdefteri.settings.BusinessSettingsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final CustomerService customerService;
    private final BusinessSettingsService businessSettingsService;
    private final FormTokenService formTokenService;
    private final ReportService reportService;
    private final Clock clock;

    public AccountController(AccountService accountService, CustomerService customerService,
                             BusinessSettingsService businessSettingsService,
                             FormTokenService formTokenService, ReportService reportService, Clock clock) {
        this.accountService = accountService;
        this.customerService = customerService;
        this.businessSettingsService = businessSettingsService;
        this.formTokenService = formTokenService;
        this.reportService = reportService;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        List<CustomerBalance> allBalances = accountService.customerBalances();
        if (q != null && !q.isBlank()) {
            String needle = SearchNormalizer.normalize(q);
            allBalances = allBalances.stream()
                    .filter(b -> SearchNormalizer.normalize(b.name()).contains(needle)
                            || (b.phone() != null && b.phone().contains(needle)))
                    .toList();
        }
        BigDecimal total = allBalances.stream().map(CustomerBalance::balance)
                .filter(b -> b.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int size = 25;
        int totalPages = Math.max(1, (allBalances.size() + size - 1) / size);
        int currentPage = Math.min(Math.max(page, 0), totalPages - 1);
        int from = currentPage * size;
        int to = Math.min(from + size, allBalances.size());
        List<CustomerBalance> pageContent = allBalances.subList(from, to);

        model.addAttribute("balances", pageContent);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("totalReceivable", total);
        model.addAttribute("page", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", allBalances.size());
        model.addAttribute("activeMenu", "accounts");
        return "accounts/list";
    }

    @GetMapping("/customer/{id}")
    public String customerLedger(@PathVariable Long id,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                 Model model) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? LocalDate.of(2000, 1, 1) : start;
        LedgerStatement statement = accountService.statement(id, startDate, endDate);
        model.addAttribute("statement", statement);
        model.addAttribute("business", businessSettingsService.get());
        model.addAttribute("activeMenu", "accounts");
        return "accounts/statement";
    }

    @GetMapping("/customer/{id}/csv")
    public ResponseEntity<byte[]> customerLedgerCsv(@PathVariable Long id,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? LocalDate.of(2000, 1, 1) : start;
        LedgerStatement statement = accountService.statement(id, startDate, endDate);
        String csv = buildCsv(statement);
        String fileName = "cari-ekstre-" + id + "-" + startDate + "-" + endDate + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(CsvUtil.toUtf8Bom(csv));
    }

    @GetMapping("/customer/{id}/xlsx")
    public ResponseEntity<byte[]> customerLedgerExcel(@PathVariable Long id,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate endDate = end == null ? LocalDate.now(clock) : end;
        LocalDate startDate = start == null ? LocalDate.of(2000, 1, 1) : start;
        LedgerStatement statement = accountService.statement(id, startDate, endDate);
        return DownloadUtil.xlsx(reportService.ledgerExcel(statement),
                "cari-ekstre-" + id + "-" + startDate + "-" + endDate + ".xlsx");
    }

    @PreAuthorize("hasRole('PATRON')")
    @GetMapping("/new-debt")
    public String debtForm(@RequestParam(required = false) Long customerId,
                           HttpSession session, Model model) {
        DebtForm form = new DebtForm();
        form.setCustomerId(customerId);
        form.setTransactionDate(LocalDate.now(clock));
        model.addAttribute("debtForm", form);
        model.addAttribute("customers", customerService.allCustomers());
        model.addAttribute("formToken", formTokenService.issue(session));
        model.addAttribute("activeMenu", "accounts");
        return "accounts/debt-form";
    }

    @PreAuthorize("hasRole('PATRON')")
    @PostMapping("/new-debt")
    public String createDebt(@Valid @ModelAttribute("debtForm") DebtForm form,
                             BindingResult bindingResult,
                             @RequestParam(name = "formToken", required = false) String formToken,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        try {
            formTokenService.consume(session, formToken);
        } catch (BusinessRuleException ex) {
            bindingResult.reject("form.token", ex.getMessage());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("customers", customerService.allCustomers());
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "accounts");
            return "accounts/debt-form";
        }
        try {
            accountService.createManualDebt(form.getCustomerId(), form.getAmount(),
                    form.getTransactionDate(), form.getDescription());
            redirectAttributes.addFlashAttribute("successMessage", "Borç kaydı oluşturuldu.");
            return "redirect:/accounts/customer/" + form.getCustomerId();
        } catch (BusinessRuleException ex) {
            bindingResult.reject("debt.failed", ex.getMessage());
            model.addAttribute("customers", customerService.allCustomers());
            model.addAttribute("formToken", formTokenService.issue(session));
            model.addAttribute("activeMenu", "accounts");
            return "accounts/debt-form";
        }
    }

    private String buildCsv(LedgerStatement statement) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.row("Müşteri", statement.customer().getName()));
        sb.append(CsvUtil.row("Dönem", statement.start() + " - " + statement.end()));
        sb.append(CsvUtil.row("Açılış Bakiyesi", csvMoney(statement.openingBalance())));
        sb.append('\n');
        sb.append(CsvUtil.row("Tarih", "Tür", "Açıklama", "İş Emri", "Borç", "Alacak", "Bakiye"));
        for (LedgerRow row : statement.rows()) {
            sb.append(CsvUtil.row(row.date(), row.type().getLabel(), row.description(),
                    row.workOrderNo(), csvMoney(row.debit()), csvMoney(row.credit()), csvMoney(row.balance())));
        }
        sb.append('\n');
        sb.append(CsvUtil.row("Toplam Borç", csvMoney(statement.totalDebit())));
        sb.append(CsvUtil.row("Toplam Alacak", csvMoney(statement.totalCredit())));
        sb.append(CsvUtil.row("Kapanış Bakiyesi", csvMoney(statement.closingBalance())));
        return sb.toString();
    }

    private String csvMoney(BigDecimal value) {
        if (value == null) {
            return "0,00";
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR"));
        DecimalFormat format = new DecimalFormat("0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }
}
