package com.momknpay.catalog;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** GET /v1/services and GET /v1/services/sync (FR-CAT-1…7). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void catalogueListsEveryVisibleServiceIncludingInactiveOnes() throws Exception {
        String body = getJson("/v1/services");

        List<String> ids = JsonPath.read(body, "$.items[*].id");
        assertThat(ids).hasSizeGreaterThanOrEqualTo(24).doesNotContain("svc_water_legacy");

        List<Boolean> alex = JsonPath.read(body, "$.items[?(@.id == 'svc_elec_alex')].isActive");
        assertThat(alex).containsExactly(false);

        Instant syncedAt = Instant.parse(JsonPath.read(body, "$.syncedAt"));
        assertThat(Duration.between(syncedAt, Instant.now()).abs())
                .isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void itemsMatchTheContractShape() throws Exception {
        String body = getJson("/v1/services");

        List<Map<String, Object>> matches =
                JsonPath.read(body, "$.items[?(@.id == 'svc_elec_cairo')]"); // filters return lists
        assertThat(matches).hasSize(1);
        Map<String, Object> cairo = matches.getFirst();
        assertThat(cairo)
                .containsOnlyKeys(
                        "id",
                        "nameEn",
                        "nameAr",
                        "category",
                        "iconUrl",
                        "inputLabel",
                        "inputPattern",
                        "minAmount",
                        "maxAmount",
                        "isActive",
                        "updatedAt")
                .containsEntry("nameEn", "Cairo Electricity")
                .containsEntry("nameAr", "كهرباء القاهرة")
                .containsEntry("category", "electricity")
                .containsEntry("inputPattern", "^[0-9]{10}$")
                .containsEntry("minAmount", 500) // integer piastres, never a float
                .containsEntry("maxAmount", 500000)
                .containsEntry("isActive", true)
                .containsEntry("updatedAt", "2026-09-18T09:00:00Z");
    }

    @Test
    void itemsAreGroupedByCategoryThenEnglishName() throws Exception {
        String body = getJson("/v1/services");

        List<String> categories = JsonPath.read(body, "$.items[*].category");
        assertThat(categories).isSorted();
        List<String> electricity =
                JsonPath.read(body, "$.items[?(@.category == 'electricity')].nameEn");
        assertThat(electricity).isSorted();
    }

    @Test
    void catalogueDoesNotNeedAUser() throws Exception {
        mvc.perform(withClientHeaders(get("/v1/services"))).andExpect(status().isOk());
    }

    @Test
    void syncFromBeforeTheSeedReturnsEverythingAndTheDeletedIds() throws Exception {
        String body = getJson("/v1/services/sync?since=2026-01-01T00:00:00Z");

        List<String> ids = JsonPath.read(body, "$.items[*].id");
        List<String> deleted = JsonPath.read(body, "$.deletedIds");
        assertThat(ids).hasSizeGreaterThanOrEqualTo(24).doesNotContain("svc_water_legacy");
        assertThat(deleted).containsExactly("svc_water_legacy");
    }

    @Test
    void syncFromTheFutureIsEmpty() throws Exception {
        String since =
                Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();

        mvc.perform(withClientHeaders(get("/v1/services/sync").param("since", since)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.deletedIds").isEmpty())
                .andExpect(jsonPath("$.syncedAt").isString());
    }

    @Test
    void aRowChangedAfterTheLastSyncIsSentAgain() throws Exception {
        String lastSync = JsonPath.read(getJson("/v1/services"), "$.syncedAt");

        jdbc.update("UPDATE services SET name_en = name_en WHERE id = 'svc_gas_egypt'"); // trigger
        // bumps
        // updated_at

        String body =
                mvc.perform(withClientHeaders(get("/v1/services/sync").param("since", lastSync)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        assertThat(ids).contains("svc_gas_egypt");
    }

    @Test
    void missingOrInvalidSinceIsValidationError() throws Exception {
        for (var request :
                List.of(
                        get("/v1/services/sync"),
                        get("/v1/services/sync").param("since", "yesterday"),
                        get("/v1/services/sync").param("since", "2026-13-45T99:00:00Z"))) {
            mvc.perform(withClientHeaders(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("since"));
        }
    }

    private String getJson(String path) throws Exception {
        return mvc.perform(withClientHeaders(get(path)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
