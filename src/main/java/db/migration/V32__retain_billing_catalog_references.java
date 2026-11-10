package db.migration;

import java.sql.Connection;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Existing unnamed foreign keys have different generated names in PostgreSQL and H2. */
public class V32__retain_billing_catalog_references extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        retain(context.getConnection(), "top_up_orders", "package_id", "credit_packages", "fk_top_up_orders_package");
        retain(context.getConnection(), "ai_credit_reservations", "rate_id", "ai_credit_rates", "fk_credit_reservations_rate");
    }

    private void retain(Connection connection, String table, String column, String parent, String constraint)
            throws Exception {
        String existingName = null;
        try (var keys = connection.getMetaData().getImportedKeys(null, connection.getSchema(), table)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))) {
                    existingName = keys.getString("FK_NAME");
                    break;
                }
            }
        }
        if (existingName == null) {
            throw new IllegalStateException("Expected billing catalog foreign key not found: " + table + "." + column);
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + existingName.replace("\"", "\"\"") + "\"");
            statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint
                    + " FOREIGN KEY (" + column + ") REFERENCES " + parent + "(id) ON DELETE RESTRICT");
        }
    }
}
