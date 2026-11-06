package tr.com.servisdefteri.dashboard;

import tr.com.servisdefteri.account.AccountTransaction;
import tr.com.servisdefteri.account.CustomerBalance;
import tr.com.servisdefteri.appointment.Appointment;
import tr.com.servisdefteri.report.MaintenanceReminder;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderStatusSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardView(long activeCustomers,
                            long activeVehicles,
                            long openWorkOrders,
                            long inProgressWorkOrders,
                            boolean financialVisible,
                            BigDecimal netReceivable,
                            long debtorCount,
                            BigDecimal todayCollections,
                            List<AccountTransaction> recentTransactions,
                            List<AccountTransaction> recentPayments,
                            List<CustomerBalance> topDebtors,
                            List<WorkOrder> pendingWorkOrders,
                            List<WorkOrder> completedToday,
                            LocalDate today,
                            List<DailyCollection> weeklyCollections,
                            List<WorkOrder> readyForDelivery,
                            List<WorkOrderStatusSummary> workOrderStatuses,
                            long totalWorkOrders,
                            List<Appointment> todayAppointments,
                            List<MaintenanceReminder> maintenanceReminders) {
}
