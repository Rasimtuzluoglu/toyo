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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
        assertThat(flyway.info().applied()).hasSize(13);

        Long tables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name in (
                    'users', 'customers', 'vehicles', 'vehicle_ownership_history',
                    'work_orders', 'work_order_items', 'payments',
                    'account_transactions', 'payment_allocations',
                    'audit_logs', 'backup_runs', 'business_settings',
                    'vehicle_intakes', 'vehicle_deliveries', 'appointments')
                """, Long.class);
        assertThat(tables).isEqualTo(15L);
    }

    @Test
    void criticalConstraintsAndIndexesExist() {
        Long uniqueDebitIndex = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname = 'uk_account_tx_work_order_debit'", Long.class);
        assertThat(uniqueDebitIndex).isEqualTo(1L);

        Long plateUnique = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and indexname = 'uk_vehicles_plate'", Long.class);
        assertThat(plateUnique).isEqualTo(1L);

        Long checkConstraints = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where constraint_schema = 'public' and constraint_type = 'CHECK'
                  and table_name in ('work_orders', 'account_transactions', 'payments', 'work_order_items')
                """, Long.class);
        assertThat(checkConstraints).isGreaterThan(5L);
    }
}
