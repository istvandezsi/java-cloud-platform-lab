package hu.dezsi.cloudlab;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    List<Task> listTasks() {
        return taskService.listTasks();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Task createTask(@RequestBody CreateTaskRequest request) {
        return taskService.createTask(request.title());
    }

    @GetMapping("/{id}")
    Task getTask(@PathVariable long id) {
        return taskService.getTask(id);
    }

    @PatchMapping("/{id}/complete")
    Task completeTask(@PathVariable long id) {
        return taskService.completeTask(id);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleTaskNotFound(TaskNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    record CreateTaskRequest(String title) {
    }

    record ErrorResponse(String message) {
    }
}
