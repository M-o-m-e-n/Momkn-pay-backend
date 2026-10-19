package com.momknpay.ad;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** GET /v1/ads (FR-AD-1…4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void listsTheSeededAdsInDisplayOrder() throws Exception {
        String body = getAds();

        List<String> ids = JsonPath.read(body, "$.items[*].id");
        assertThat(ids).containsExactly("ad_nbe", "ad_vodafone", "ad_eand", "ad_orange", "ad_we");

        List<Map<String, Object>> items = JsonPath.read(body, "$.items[*]");
        assertThat(items.getFirst())
                .containsOnlyKeys("id", "titleEn", "titleAr", "imageUrl", "linkUrl")
                .containsEntry("linkUrl", "https://www.nbe.com.eg")
                .containsEntry("imageUrl", "https://momkn.ahmedsayed.net/ads/ad-nbe.png");
    }

    @Test
    void everyLinkIsHttpsAndEveryImageIsServedWithoutHeaders() throws Exception {
        List<Map<String, Object>> items = JsonPath.read(getAds(), "$.items[*]");

        for (Map<String, Object> item : items) {
            assertThat((String) item.get("linkUrl")).startsWith("https://");
            // an image loader sends none of the API headers
            mvc.perform(get(URI.create((String) item.get("imageUrl")).getPath()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG));
        }
    }

    @Test
    @Transactional // rolled back, so the other tests keep the seed
    void anInactiveAdIsNotListed() throws Exception {
        jdbc.update("UPDATE ads SET is_active = FALSE WHERE id = 'ad_eand'");

        List<String> ids = JsonPath.read(getAds(), "$.items[*].id");
        assertThat(ids).doesNotContain("ad_eand").hasSize(4);
    }

    @Test
    @Transactional
    void theDatabaseRefusesALinkThatIsNotHttps() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE ads SET link_url = 'javascript:alert(1)'"
                                                + " WHERE id = 'ad_we'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ads_link_https");
    }

    private String getAds() throws Exception {
        return mvc.perform(withClientHeaders(get("/v1/ads")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
