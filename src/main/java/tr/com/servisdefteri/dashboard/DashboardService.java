package tr.com.servisdefteri.dashboard;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.account.AccountService;
import tr.com.servisdefteri.account.AccountTransaction;
import tr.com.servisdefteri.account.AccountTransactionRepository;
import tr.com.servisdefteri.account.CustomerBalance;
import tr.com.servisdefteri.account.TransactionType;
import tr.com.servisdefteri.appointment.Appointment;
import tr.com.servisdefteri.appointment.AppointmentService;
import tr.com.servisdefteri.customer.CustomerService;
import tr.com.servisdefteri.payment.CollectionSummary;
import tr.com.servisdefteri.payment.PaymentService;
import tr.com.servisdefteri.report.ReportService;
import tr.com.servisdefteri.vehicle.VehicleService;
import tr.com.servisdefteri.workorder.WorkOrderService;
import tr.com.servisdefteri.workorder.WorkOrderStatusSummary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final CustomerService customerService;
    private final VehicleService vehicleService;
    private final WorkOrderService workOrderService;
    private final PaymentService paymentService;
    private final AccountService accountService;
    private final AccountTransactionRepository transactionRepository;
    private final AppointmentService appointmentService;
    private final ReportService reportService;
    private final Clock clock;

    public DashboardService(CustomerService customerService, VehicleService vehicleService,
                            WorkOrderService workOrderService, PaymentService paymentService,
                            AccountService accountService, AccountTransactionRepository transactionRepository,
                            AppointmentService appointmentService, ReportService reportService, Clock clock) {
        this.customerService = customerService;
        this.vehicleService = vehicleService;
        this.workOrderService = workOrderService;
        this.paymentService = paymentService;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.appointmentService = appointmentService;
        this.reportService = reportService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView load(boolean financialVisible) {
        LocalDate today = LocalDate.now(clock);
        List<CustomerBalance> balances = financialVisible
                ? accountService.customerBalances() : List.of();
        BigDecimal netReceivable = balances.stream()
                .map(CustomerBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long debtorCount = balances.stream().filter(CustomerBalance::isDebtor).count();
        List<CustomerBalance> topDebtors = balances.stream()
                .filter(CustomerBalance::isDebtor)
                .limit(5)
                .toList();
        List<AccountTransaction> recent = financialVisible
                ? transactionRepository.findRecent(PageRequest.of(0, 8)) : List.of();
        List<AccountTransaction> recentPayments = recent.stream()
                .filter(tx -> tx.getType() == TransactionType.ALACAK)
                .limit(5)
                .toList();

        Map<LocalDate, BigDecimal> collected = paymentService.summaryBetween(today.minusDays(6), today).stream()
                .collect(Collectors.groupingBy(CollectionSummary::date,
                        Collectors.reducing(BigDecimal.ZERO, CollectionSummary::total, BigDecimal::add)));
        List<DailyCollection> rawWeekly = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            rawWeekly.add(new DailyCollection(day, collected.getOrDefault(day, BigDecimal.ZERO), 0));
        }
        BigDecimal maxWeekly = rawWeekly.stream().map(DailyCollection::amount)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<DailyCollection> weeklyCollections = rawWeekly.stream()
                .map(day -> new DailyCollection(day.date(), day.amount(),
                        maxWeekly.signum() == 0 ? 0 : day.amount()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(maxWeekly, 0, java.math.RoundingMode.HALF_UP).intValue()))
                .toList();

        List<WorkOrderStatusSummary> statuses = workOrderService.statusSummary();
        long totalWorkOrders = statuses.stream().mapToLong(WorkOrderStatusSummary::count).sum();

        return new DashboardView(
                customerService.activeCount(),
                vehicleService.activeCount(),
                workOrderService.openCount(),
                financialVisible,
                financialVisible ? netReceivable : BigDecimal.ZERO,
                financialVisible ? debtorCount : 0,
                financialVisible ? paymentService.todayTotal() : BigDecimal.ZERO,
                recent,
                recentPayments,
                topDebtors,
                workOrderService.openWorkOrders(8),
                workOrderService.finishedToday(),
                today,
                weeklyCollections,
                workOrderService.readyForDelivery(5),
                statuses,
                totalWorkOrders,
                appointmentService.day(today),
                reportService.maintenanceReminders().stream().limit(5).toList());
    }
}
