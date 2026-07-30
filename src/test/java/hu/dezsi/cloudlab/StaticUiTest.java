package hu.dezsi.cloudlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaticUiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void indexPageIsServed() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Task Board")))
                .andExpect(content().string(containsString("/assets/logo.svg")))
                .andExpect(content().string(containsString("/assets/favicon.svg")))
                .andExpect(content().string(containsString("/theme.js")))
                .andExpect(content().string(containsString("theme-preference")));
    }

    @Test
    void appJavaScriptIsServed() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/tasks")));
    }

    @Test
    void themeJavaScriptIsServed() throws Exception {
        mockMvc.perform(get("/theme.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cloudlab-theme")))
                .andExpect(content().string(containsString("prefers-color-scheme")));
    }

    @Test
    void stylesAreServed() throws Exception {
        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("task-item")))
                .andExpect(content().string(containsString("[data-theme=\"dark\"]")));
    }

    @Test
    void projectLogoIsServed() throws Exception {
        mockMvc.perform(get("/assets/logo.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().string(containsString("Java Cloud Platform Lab logo")));
    }

    @Test
    void faviconIsServed() throws Exception {
        mockMvc.perform(get("/assets/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
    }
}
