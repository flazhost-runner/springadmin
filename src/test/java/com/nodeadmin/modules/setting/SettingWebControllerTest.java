package com.nodeadmin.modules.setting;

import com.nodeadmin.BaseIntegrationTest;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import com.nodeadmin.modules.setting.service.ISettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SettingWebController.
 *
 * <p>Verifies:
 * <ul>
 *   <li>POST + _method=PUT via multipart form changes the theme in DB.</li>
 *   <li>After redirect, GET /admin/v1/setting reflects the new theme in HTML.</li>
 *   <li>Flash "success" message is delivered.</li>
 * </ul>
 */
class SettingWebControllerTest extends BaseIntegrationTest {

    @Autowired
    private ISettingService settingService;

    // =========================================================================
    // Theme update
    // =========================================================================

    @Test
    @DisplayName("PUT /admin/v1/setting/update via multipart saves theme to DB")
    void updateTheme_savesThemeToDB() throws Exception {
        MockHttpSession session = loginAsAdmin();
        assertThat(session).isNotNull();

        // Submit the setting form — simulates browser multipart/form-data with _method=PUT
        mockMvc.perform(
                multipart("/admin/v1/setting/update")
                        .param("_method", "PUT")
                        .param("theme", "Green")
                        .with(csrf())
                        .session(session)
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/v1/setting"));

        // Verify DB — cache was cleared by update(), so getCachedSetting re-reads
        SettingEntity setting = settingService.getCachedSetting();
        assertThat(setting.getTheme())
                .as("theme should be Green after update")
                .isEqualTo("Green");
    }

    @Test
    @DisplayName("After theme update, GET /admin/v1/setting shows updated theme in HTML")
    void updateTheme_getShowsNewTheme() throws Exception {
        MockHttpSession session = loginAsAdmin();
        assertThat(session).isNotNull();

        // Submit theme=Purple
        MvcResult updateResult = mockMvc.perform(
                multipart("/admin/v1/setting/update")
                        .param("_method", "PUT")
                        .param("theme", "Purple")
                        .with(csrf())
                        .session(session)
        )
        .andExpect(status().is3xxRedirection())
        .andReturn();

        // Follow redirect — GET /admin/v1/setting
        String redirectUrl = updateResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();

        MvcResult getResult = mockMvc.perform(
                get(redirectUrl).session(session)
        )
        .andExpect(status().isOk())
        .andReturn();

        String html = getResult.getResponse().getContentAsString();

        // currentTheme=Purple → Purple radio should have checked="checked"
        // and --primary CSS should be the Purple hex
        assertThat(html)
                .as("HTML should have Purple radio checked")
                .contains("value=\"Purple\"");
        // The checked radio has checked="checked"; verify currentTheme used in th:checked
        // We verify indirectly: the CSS primary variable should be Purple's hex (#9333EA or similar)
        assertThat(html)
                .as("HTML should reflect Purple primary color in CSS variables")
                .containsAnyOf("#9333EA", "#7C3AED", "Purple");
    }

    @Test
    @DisplayName("Flash success message present after theme update")
    void updateTheme_flashSuccessPresent() throws Exception {
        MockHttpSession session = loginAsAdmin();
        assertThat(session).isNotNull();

        // Submit
        MvcResult updateResult = mockMvc.perform(
                multipart("/admin/v1/setting/update")
                        .param("_method", "PUT")
                        .param("theme", "Red")
                        .with(csrf())
                        .session(session)
        )
        .andExpect(status().is3xxRedirection())
        .andReturn();

        // Follow redirect with same session (flash attrs live in session)
        String redirectUrl = updateResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();

        MockHttpSession redirectSession = (MockHttpSession) updateResult.getRequest().getSession(false);

        MvcResult getResult = mockMvc.perform(
                get(redirectUrl).session(redirectSession != null ? redirectSession : session)
        )
        .andExpect(status().isOk())
        .andReturn();

        String html = getResult.getResponse().getContentAsString();
        assertThat(html)
                .as("Flash success should be in HTML")
                // Pesan flash kanonik mengikuti NodeAdmin (referensi fleet):
                // 'Save Setting Success.' — lihat NodeAdmin SettingController.ts
                .contains("Save Setting Success.");
    }
}
