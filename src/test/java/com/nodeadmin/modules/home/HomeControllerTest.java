package com.nodeadmin.modules.home;

import com.nodeadmin.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Landing publik `/` — slug default (agency-consulting-002-creative-agency, seed)
 * harus merender view native landing v6 (marker `_v6_001`), BUKAN mengunduh dari
 * GitHub (regresi: URL tanpa subpath landings/ → 404 TEMPLATE_NOT_FOUND berulang).
 */
class HomeControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET / renders the bundled v6 landing for the default slug")
    void landingRendersV6ForDefaultSlug() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "_hero_digital_agency_v6_001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "_footer_dark_subscribe_v6_001")));
    }

    @Test
    @DisplayName("GET /home is an alias of the landing")
    void homeAliasRendersLanding() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "_hero_digital_agency_v6_001")));
    }
}
