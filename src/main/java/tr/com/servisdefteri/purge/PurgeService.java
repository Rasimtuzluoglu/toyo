package tr.com.servisdefteri.purge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.audit.AuditAction;
import tr.com.servisdefteri.audit.AuditService;
import tr.com.servisdefteri.common.BusinessRuleException;
import tr.com.servisdefteri.common.NotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PATRON tarafından yapılan kalıcı silme işlemleri.
 * Kayıtlar arasındaki bağlar tek transaction içinde, bağımlılık sırasıyla temizlenir.
 */
@Service
public class PurgeService {

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public PurgeService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PurgeSummary summaryCustomer(Long id) {
        String name = one("select name from customers where id = ?", String.class, id);
        Map<String, String> counts = new LinkedHashMap<>();
        counts.put("Araç", String.valueOf(count("select count(*) from vehicles where customer_id = ?", id)));
        counts.put("İş emri", String.valueOf(count("select count(*) from work_orders where customer_id = ?", id)));
        counts.put("Tahsilat", String.valueOf(count("select count(*) from payments where customer_id = ?", id)));
        counts.put("Randevu", String.valueOf(count("select count(*) from appointments where customer_id = ?", id)));
        return new PurgeSummary("customer", id, name, "Müşteriyi Kalıcı Sil",
                "Müşteriye bağlı tüm araçlar, iş emirleri, tahsilatlar, cari hareketler ve belgeler "
                        + "kalıcı olarak silinecek. Bu işlem geri alınamaz.", counts);
    }

    @Transactional(readOnly = true)
    public PurgeSummary summaryVehicle(Long id) {
        String plate = one("select plate from vehicles where id = ?", String.class, id);
        Map<String, String> counts = new LinkedHashMap<>();
        counts.put("İş emri", String.valueOf(count("select count(*) from work_orders where vehicle_id = ?", id)));
        counts.put("Randevu", String.valueOf(count("select count(*) from appointments where vehicle_id = ?", id)));
        return new PurgeSummary("vehicle", id, plate, "Aracı Kalıcı Sil",
                "Aracın iş emirleri, kalemleri, belgeleri ve bu iş emirlerine bağlı tahsilatlar "
                        + "kalıcı olarak silinecek. Bu işlem geri alınamaz.", counts);
    }

    @Transactional(readOnly = true)
    public PurgeSummary summaryWorkOrder(Long id) {
        String orderNo = one("select order_no from work_orders where id = ?", String.class, id);
        List<Long> transactionIds = ids("select id from account_transactions where work_order_id = ?", id);
        long payments = transactionIds.isEmpty() ? 0
                : countIn("select count(distinct payment_id) from payment_allocations where transaction_id",
                transactionIds);
        Map<String, String> counts = new LinkedHashMap<>();
        counts.put("Kalem", String.valueOf(count("select count(*) from work_order_items where work_order_id = ?", id)));
        counts.put("Bağlı tahsilat", String.valueOf(payments));
        counts.put("Kabul/teslim belgesi", String.valueOf(count(
                "select (select count(*) from vehicle_intakes where work_order_id = ?) + "
                        + "(select count(*) from vehicle_deliveries where work_order_id = ?)", id, id)));
        return new PurgeSummary("work-order", id, orderNo, "İş Emrini Kalıcı Sil",
                "İş emri, kalemleri, belgeleri ve bu iş emrine bağlı tahsilatlar koşulsuz silinecek. "
                        + "Tahsilat başka iş emirlerine dağıtılmışsa bu dağıtımlar da silinir. "
                        + "Bu işlem geri alınamaz.", counts);
    }

    @Transactional(readOnly = true)
    public PurgeSummary summaryPayment(Long id) {
        String receiptNo = one("select receipt_no from payments where id = ?", String.class, id);
        BigDecimal amount = one("select amount from payments where id = ?", BigDecimal.class, id);
        Map<String, String> counts = new LinkedHashMap<>();
        counts.put("Tutar", amount == null ? "0,00 ₺" : amount.setScale(2) + " ₺");
        counts.put("Dağıtım", String.valueOf(count("select count(*) from payment_allocations where payment_id = ?", id)));
        return new PurgeSummary("payment", id, receiptNo, "Tahsilatı Kalıcı Sil",
                "Tahsilat, dağıtımları ve cari alacak hareketi kalıcı olarak silinecek. "
                        + "İş emri borcu yeniden açılabilir. Bu işlem geri alınamaz.", counts);
    }

    @Transactional
    public void purgeCustomer(Long id) {
        String name = one("select name from customers where id = ?", String.class, id);
        List<Long> vehicleIds = ids("select id from vehicles where customer_id = ?", id);
        List<Long> workOrderIds = ids("select id from work_orders where customer_id = ?", id);
        List<Long> paymentIds = ids("select id from payments where customer_id = ?", id);
        Set<Long> transactionIds = new LinkedHashSet<>(
                ids("select id from account_transactions where customer_id = ?", id));

        // Araç başka bir müşteriye devredilmişse eski sahibin iş emirleri hâlâ
        // vehicle_id ile bu araca bağlıdır. Bunları sessizce silmek başka bir
        // müşterinin finansal geçmişini yok eder; bu yüzden erken ve açık hata ver.
        List<String> foreignOrders = jdbc.queryForList("""
                select o.order_no
                from work_orders o
                join vehicles v on v.id = o.vehicle_id
                where v.customer_id = ? and o.customer_id <> ?
                order by o.order_no
                """, String.class, id, id);
        if (!foreignOrders.isEmpty()) {
            throw new BusinessRuleException("Bu müşterinin araçlarında başka müşterilere ait iş emirleri var: "
                    + String.join(", ", foreignOrders)
                    + ". Bu araçlar silinemediği için müşteri kalıcı olarak silinemez; "
                    + "önce ilgili iş emirlerini tek tek kalıcı silin.");
        }

        auditService.record(AuditAction.CUSTOMER_PURGED, "Müşteri", id,
                "Müşteri kalıcı silindi: id=" + id + ", araç=" + vehicleIds.size()
                        + ", iş=" + workOrderIds.size() + ", tahsilat=" + paymentIds.size());

        deleteAllocations(paymentIds, transactionIds);
        deleteTransactions(transactionIds);
        deletePayments(paymentIds);
        deleteWorkOrderChildren(workOrderIds);
        deleteWorkOrders(workOrderIds);
        deleteAppointmentsForCustomer(id, vehicleIds);
        deleteOwnershipForCustomer(id, vehicleIds);
        deleteVehicles(vehicleIds);
        jdbc.update("delete from customers where id = ?", id);
    }

    @Transactional
    public void purgeVehicle(Long id) {
        String plate = one("select plate from vehicles where id = ?", String.class, id);
        List<Long> workOrderIds = ids("select id from work_orders where vehicle_id = ?", id);
        Set<Long> transactionIds = new LinkedHashSet<>(workOrderIds.isEmpty() ? List.of()
                : idsIn("select id from account_transactions where work_order_id", workOrderIds));
        Set<Long> paymentIds = new LinkedHashSet<>(transactionIds.isEmpty() ? List.of()
                : idsIn("select distinct payment_id from payment_allocations where transaction_id",
                transactionIds));
        if (!paymentIds.isEmpty()) {
            transactionIds.addAll(idsIn("select id from account_transactions where payment_id", paymentIds));
        }
        transactionIds = withReversals(transactionIds);

        auditService.record(AuditAction.VEHICLE_PURGED, "Araç", id,
                "Araç kalıcı silindi: id=" + id + ", iş=" + workOrderIds.size()
                        + ", tahsilat=" + paymentIds.size());

        deleteAllocations(paymentIds, transactionIds);
        deleteTransactions(transactionIds);
        deletePayments(paymentIds);
        deleteWorkOrderChildren(workOrderIds);
        deleteWorkOrders(workOrderIds);
        deleteAppointmentsForVehicle(id);
        deleteOwnershipForVehicle(id);
        jdbc.update("delete from vehicles where id = ?", id);
    }

    @Transactional
    public void purgeWorkOrder(Long id) {
        String orderNo = one("select order_no from work_orders where id = ?", String.class, id);
        Set<Long> transactionIds = new LinkedHashSet<>(
                ids("select id from account_transactions where work_order_id = ?", id));
        Set<Long> paymentIds = new LinkedHashSet<>(transactionIds.isEmpty() ? List.of()
                : idsIn("select distinct payment_id from payment_allocations where transaction_id",
                transactionIds));
        if (!paymentIds.isEmpty()) {
            transactionIds.addAll(idsIn("select id from account_transactions where payment_id", paymentIds));
        }
        transactionIds = withReversals(transactionIds);

        auditService.record(AuditAction.WORK_ORDER_PURGED, "İşEmri", id,
                "İş emri kalıcı silindi: id=" + id + ", tahsilat=" + paymentIds.size());

        deleteAllocations(paymentIds, transactionIds);
        deleteTransactions(transactionIds);
        deletePayments(paymentIds);
        deleteWorkOrderChildren(List.of(id));
        jdbc.update("delete from work_orders where id = ?", id);
    }

    @Transactional
    public void purgePayment(Long id) {
        String receiptNo = one("select receipt_no from payments where id = ?", String.class, id);
        BigDecimal amount = one("select amount from payments where id = ?", BigDecimal.class, id);
        Set<Long> transactionIds = withReversals(new LinkedHashSet<>(
                ids("select id from account_transactions where payment_id = ?", id)));

        auditService.record(AuditAction.PAYMENT_PURGED, "Tahsilat", id,
                "Tahsilat kalıcı silindi: id=" + id + ", tutar=" + amount);

        deleteAllocations(List.of(id), transactionIds);
        deleteTransactions(transactionIds);
        jdbc.update("delete from payments where id = ?", id);
    }

    private void deleteAllocations(Collection<Long> paymentIds, Collection<Long> transactionIds) {
        deleteIn("payment_allocations", "payment_id", paymentIds);
        deleteIn("payment_allocations", "transaction_id", transactionIds);
    }

    private void deleteTransactions(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return;
        }
        clearReversalLinks(transactionIds);
        deleteIn("account_transactions", "id", transactionIds);
    }

    private void deletePayments(Collection<Long> paymentIds) {
        deleteIn("payments", "id", paymentIds);
    }

    private void deleteWorkOrderChildren(Collection<Long> workOrderIds) {
        deleteIn("vehicle_intakes", "work_order_id", workOrderIds);
        deleteIn("vehicle_deliveries", "work_order_id", workOrderIds);
        deleteIn("work_order_items", "work_order_id", workOrderIds);
    }

    private void deleteWorkOrders(Collection<Long> workOrderIds) {
        deleteIn("work_orders", "id", workOrderIds);
    }

    private void deleteAppointmentsForCustomer(Long customerId, Collection<Long> vehicleIds) {
        jdbc.update("delete from appointments where customer_id = ?", customerId);
        deleteIn("appointments", "vehicle_id", vehicleIds);
    }

    private void deleteAppointmentsForVehicle(Long vehicleId) {
        jdbc.update("delete from appointments where vehicle_id = ?", vehicleId);
    }

    private void deleteOwnershipForCustomer(Long customerId, Collection<Long> vehicleIds) {
        deleteIn("vehicle_ownership_history", "vehicle_id", vehicleIds);
        jdbc.update("""
                delete from vehicle_ownership_history
                where previous_customer_id = ? or new_customer_id = ?
                """, customerId, customerId);
    }

    private void deleteOwnershipForVehicle(Long vehicleId) {
        jdbc.update("delete from vehicle_ownership_history where vehicle_id = ?", vehicleId);
    }

    private void deleteVehicles(Collection<Long> vehicleIds) {
        deleteIn("vehicles", "id", vehicleIds);
    }

    private Set<Long> withReversals(Set<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return rootIds;
        }
        Set<Long> all = new LinkedHashSet<>(rootIds);
        List<Long> frontier = new ArrayList<>(rootIds);
        while (!frontier.isEmpty()) {
            List<Long> next = idsIn("select id from account_transactions where reversal_of_id", frontier);
            next.removeAll(all);
            if (next.isEmpty()) {
                break;
            }
            all.addAll(next);
            frontier = next;
        }
        return all;
    }

    private void clearReversalLinks(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update("update account_transactions set reversal_of_id = null where reversal_of_id in ("
                + placeholders(ids) + ")", ids.toArray());
    }

    private int deleteIn(String table, String column, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jdbc.update("delete from " + table + " where " + column + " in ("
                + placeholders(ids) + ")", ids.toArray());
    }

    private List<Long> ids(String sql, Object... args) {
        return jdbc.queryForList(sql, Long.class, args);
    }

    private List<Long> idsIn(String sqlPrefix, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList(sqlPrefix + " in (" + placeholders(ids) + ")",
                Long.class, ids.toArray());
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long countIn(String sqlPrefix, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Long value = jdbc.queryForObject(sqlPrefix + " in (" + placeholders(ids) + ")",
                Long.class, ids.toArray());
        return value == null ? 0 : value;
    }

    private <T> T one(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbc.queryForList(sql, type, args);
        if (rows.isEmpty()) {
            throw new NotFoundException("Kayıt bulunamadı.");
        }
        return rows.get(0);
    }

    private String placeholders(Collection<?> values) {
        return values.stream().map(value -> "?").collect(Collectors.joining(","));
    }
}
