package com.momknpay.db.migration;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Contract v4.0.0: payments are confirmed with a 3-digit CVV instead of the 4-digit PIN (LLD
 * §11.5). Renames the PIN columns and gives every user the CVV {@code 123}. A Java migration, like
 * V3, so the hash comes from the real bcrypt encoder: no hash literals in the repository.
 */
public class V8__PinToCvv extends BaseJavaMigration {

    private static final int BCRYPT_COST = 12;
    private static final String TEST_CVV = "123";

    @Override
    public void migrate(Context context) throws SQLException {
        try (Statement ddl = context.getConnection().createStatement()) {
            ddl.execute("ALTER TABLE users RENAME COLUMN pin_hash TO cvv_hash");
            ddl.execute(
                    "ALTER TABLE inquiries RENAME COLUMN failed_pin_attempts TO"
                            + " failed_cvv_attempts");
        }
        try (PreparedStatement update =
                context.getConnection().prepareStatement("UPDATE users SET cvv_hash = ?")) {
            update.setString(1, new BCryptPasswordEncoder(BCRYPT_COST).encode(TEST_CVV));
            update.executeUpdate();
        }
    }
}
