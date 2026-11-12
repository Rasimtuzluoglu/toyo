package tr.com.servisdefteri.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tr.com.servisdefteri.support.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends IntegrationTestSupport {

    @Autowired
    private Flyway flyway;

    @Test
    void allMigrationsAreAppliedAndExpectedTablesExist() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(flyway.info().applied()).hasSize(19);

        Long tables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name in (
                    'users', 'customers', 'vehicles', 'vehicle_ownership_history',
                    'work_orders', 'work_order_items', 'payments',
                    'account_transactions', 'payment_allocations',
                    'audit_logs', 'backup_runs', 'business_settings',
                    'vehicle_intakes', 'vehicle_deliveries', 'appointments',
                    'installation_state')
                """, Long.class);
        assertThat(tables).isEqualTo(16L);
    }

    @Test
    void criticalConstraintsAndIndexesExist() {
        Long uniqueDebitIndex = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname = 'uk_account_tx_work_order_active_debit'", Long.class);
        assertThat(uniqueDebitIndex).isEqualTo(1L);

        Long activeDebitColumn = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'account_transactions'
                  and column_name = 'active_debit'
                """, Long.class);
        assertThat(activeDebitColumn).isEqualTo(1L);

        Long assignedNameColumn = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'work_orders'
                  and column_name = 'assigned_name'
                """, Long.class);
        assertThat(assignedNameColumn).isEqualTo(1L);

        Long plateUnique = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname = 'uk_vehicles_plate'", Long.class);
        assertThat(plateUnique).isEqualTo(1L);

        for (String index : new String[]{
                "uk_work_orders_order_no_lower", "uk_customers_phone_name",
                "uk_appointments_vehicle_slot", "idx_audit_action_created",
                "idx_payments_status_date", "idx_work_orders_status_opened",
                "idx_account_tx_customer_date", "idx_account_tx_reversal"}) {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
                    Long.class, index);
            assertThat(count).as(index).isEqualTo(1L);
        }

        Long checkConstraints = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where constraint_schema = 'public' and constraint_type = 'CHECK'
                  and table_name in ('work_orders', 'account_transactions', 'payments', 'work_order_items')
                """, Long.class);
        assertThat(checkConstraints).isGreaterThan(5L);

        String statusConstraint = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conname = 'ck_work_orders_status'
                """, String.class);
        assertThat(statusConstraint).doesNotContain("BEKLIYOR");
    }
}
