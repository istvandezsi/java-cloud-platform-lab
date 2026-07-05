package hu.dezsi.cloudlab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    Task createTask(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(request.title());
    }

    @GetMapping("/{id}")
    Task getTask(@PathVariable long id) {
        return taskService.getTask(id);
    }

    @PatchMapping("/{id}")
    Task updateTaskTitle(@PathVariable long id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.updateTaskTitle(id, request.title());
    }

    @PatchMapping("/{id}/complete")
    Task completeTask(@PathVariable long id) {
        return taskService.completeTask(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTask(@PathVariable long id) {
        taskService.deleteTask(id);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleValidationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Invalid request");

        return new ErrorResponse(message);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleTaskNotFound(TaskNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    record CreateTaskRequest(@NotBlank(message = "Task title must not be blank") String title) {
    }

    record UpdateTaskRequest(@NotBlank(message = "Task title must not be blank") String title) {
    }

    record ErrorResponse(String message) {
    }
}
