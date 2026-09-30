package com.momknpay.catalog;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** GET /v1/services (FR-CAT-1…3, FR-CAT-7). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIT {

    @Autowired private MockMvc mvc;

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

    private String getJson(String path) throws Exception {
        return mvc.perform(withClientHeaders(get(path)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
