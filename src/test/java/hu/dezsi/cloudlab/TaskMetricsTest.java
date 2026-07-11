package hu.dezsi.cloudlab;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void recordsSuccessfulTaskOperation() throws Exception {
        double before = counterValue("create", "success");

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Verify task metrics"}
                                """))
                .andExpect(status().isCreated());

        assertThat(counterValue("create", "success"))
                .isEqualTo(before + 1);
    }

    @Test
    void recordsNotFoundTaskOperation() throws Exception {
        double before = counterValue("get", "not_found");

        mockMvc.perform(get("/api/tasks/{id}", 999999))
                .andExpect(status().isNotFound());

        assertThat(counterValue("get", "not_found"))
                .isEqualTo(before + 1);
    }

    @Test
    void recordsValidationError() throws Exception {
        double before = counterValue("create", "validation_error");

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(counterValue("create", "validation_error"))
                .isEqualTo(before + 1);
    }

    private double counterValue(String operation, String outcome) {
        Counter counter = meterRegistry.find(TaskMetrics.METRIC_NAME)
                .tags(
                        "operation", operation,
                        "outcome", outcome
                )
                .counter();

        return counter == null ? 0 : counter.count();
    }
}
