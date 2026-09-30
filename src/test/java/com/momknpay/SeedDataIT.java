package com.momknpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** The seed data every team tests against (FR-SEED-1…3). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SeedDataIT {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void atLeast24VisibleServicesAcrossAllSixCategories() {
        Integer visible =
                jdbc.queryForObject(
                        "SELECT count(*) FROM services WHERE deleted_at IS NULL", Integer.class);
        List<String> categories =
                jdbc.queryForList(
                        "SELECT DISTINCT category FROM services WHERE deleted_at IS NULL",
                        String.class);

        assertThat(visible).isGreaterThanOrEqualTo(24);
        assertThat(categories)
                .containsExactlyInAnyOrder(
                        "electricity", "water", "gas", "internet", "mobile", "landline");
    }

    @Test
    void everyServiceHasArabicAndEnglishNames() {
        Integer missing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM services WHERE trim(name_en) = '' OR trim(name_ar) ="
                                + " ''",
                        Integer.class);

        assertThat(missing).isZero();
    }

    @Test
    void mockRuleServicesArePresent() {
        List<String> slow =
                jdbc.queryForList(
                        "SELECT id FROM services WHERE id LIKE '%\\_slow' AND deleted_at IS NULL",
                        String.class);
        List<String> inactive =
                jdbc.queryForList(
                        "SELECT id FROM services WHERE NOT is_active AND deleted_at IS NULL",
                        String.class);
        List<String> deleted =
                jdbc.queryForList(
                        "SELECT id FROM services WHERE deleted_at IS NOT NULL", String.class);

        assertThat(slow).isNotEmpty();
        assertThat(inactive).contains("svc_elec_alex");
        assertThat(deleted).containsExactly("svc_water_legacy");
    }

    @Test
    void theThreeTestUsersHaveWorkingPins() {
        var encoder = new BCryptPasswordEncoder();
        Map<String, String> pinHashes =
                Map.of(
                        "usr_01", pinHashOf("usr_01"),
                        "usr_02", pinHashOf("usr_02"),
                        "usr_03", pinHashOf("usr_03"));

        assertThat(encoder.matches("1234", pinHashes.get("usr_01"))).isTrue();
        assertThat(encoder.matches("1234", pinHashes.get("usr_02"))).isTrue();
        assertThat(encoder.matches("9999", pinHashes.get("usr_03"))).isTrue();
        assertThat(encoder.matches("1234", pinHashes.get("usr_03"))).isFalse();
        assertThat(pinHashes.values()).allSatisfy(hash -> assertThat(hash).startsWith("$2a$12$"));
    }

    @Test
    void seededMobilesMatchTheBrief() {
        List<String> mobiles =
                jdbc.queryForList("SELECT mobile FROM users ORDER BY id", String.class);

        assertThat(mobiles).containsExactly("01000000001", "01000000002", "01000000003");
    }

    private String pinHashOf(String userId) {
        return jdbc.queryForObject("SELECT pin_hash FROM users WHERE id = ?", String.class, userId);
    }
}
