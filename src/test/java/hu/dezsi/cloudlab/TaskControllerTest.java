package hu.dezsi.cloudlab;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(TaskService.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createTaskReturnsCreatedTask() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write task API tests"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title", is("Write task API tests")))
                .andExpect(jsonPath("$.completed", is(false)));
    }

    @Test
    void listTasksIncludesCreatedTask() throws Exception {
        createTask("List task API");

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[*].title", hasItem("List task API")));
    }

    @Test
    void getTaskReturnsCreatedTask() throws Exception {
        long taskId = createTask("Fetch task by id");

        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is((int) taskId)))
                .andExpect(jsonPath("$.title", is("Fetch task by id")))
                .andExpect(jsonPath("$.completed", is(false)));
    }

    @Test
    void updateTaskTitleChangesTitleAndKeepsCompletedStatus() throws Exception {
        long taskId = createTask("Original title");

        mockMvc.perform(patch("/api/tasks/{id}/complete", taskId))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/tasks/{id}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is((int) taskId)))
                .andExpect(jsonPath("$.title", is("Updated title")))
                .andExpect(jsonPath("$.completed", is(true)));
    }

    @Test
    void updateMissingTaskReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/tasks/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("Task not found: 999999")));
    }

    @Test
    void completeTaskMarksTaskCompleted() throws Exception {
        long taskId = createTask("Complete task");

        mockMvc.perform(patch("/api/tasks/{id}/complete", taskId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is((int) taskId)))
                .andExpect(jsonPath("$.title", is("Complete task")))
                .andExpect(jsonPath("$.completed", is(true)));
    }

    @Test
    void deleteTaskRemovesTask() throws Exception {
        long taskId = createTask("Delete task");

        mockMvc.perform(delete("/api/tasks/{id}", taskId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Task not found: " + taskId)));
    }

    @Test
    void missingTaskReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("Task not found: 999999")));
    }

    private long createTask(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
