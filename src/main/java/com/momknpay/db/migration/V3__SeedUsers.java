package com.momknpay.db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Seeds the three test users from the brief (LLD §11.2). A Java migration so PIN hashes come from
 * the real bcrypt encoder at migration time — no hash literals in the repository.
 */
public class V3__SeedUsers extends BaseJavaMigration {

    private static final int BCRYPT_COST = 12;

    private record SeedUser(
            String id, String fullName, String mobile, String email, String pin, String since) {}

    private static final List<SeedUser> USERS =
            List.of(
                    new SeedUser(
                            "usr_01",
                            "Mina Adel",
                            "01000000001",
                            "mina@example.com",
                            "1234",
                            "2026-09-01T09:00:00Z"),
                    new SeedUser(
                            "usr_02",
                            "Sara Hassan",
                            "01000000002",
                            "sara@example.com",
                            "1234",
                            "2026-09-15T09:00:00Z"),
                    new SeedUser(
                            "usr_03",
                            "Omar Khaled",
                            "01000000003",
                            "omar@example.com",
                            "9999",
                            "2026-09-15T09:00:00Z"));

    @Override
    public void migrate(Context context) throws SQLException {
        var encoder = new BCryptPasswordEncoder(BCRYPT_COST);
        Connection connection = context.getConnection();
        try (PreparedStatement insert =
                connection.prepareStatement(
                        "INSERT INTO users (id, full_name, mobile, email, pin_hash, member_since,"
                                + " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (SeedUser user : USERS) {
                Timestamp since = Timestamp.from(Instant.parse(user.since()));
                insert.setString(1, user.id());
                insert.setString(2, user.fullName());
                insert.setString(3, user.mobile());
                insert.setString(4, user.email());
                insert.setString(5, encoder.encode(user.pin()));
                insert.setTimestamp(6, since);
                insert.setTimestamp(7, since);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
