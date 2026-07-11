package hu.dezsi.cloudlab;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Tasks", description = "Create, read, update, complete, and delete tasks")
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskMetrics taskMetrics;

    public TaskController(TaskService taskService, TaskMetrics taskMetrics) {
        this.taskService = taskService;
        this.taskMetrics = taskMetrics;
    }

    @Operation(summary = "List tasks")
    @ApiResponse(responseCode = "200", description = "Tasks returned")
    @GetMapping
    List<Task> listTasks() {
        return taskMetrics.record("list", taskService::listTasks);
    }

    @Operation(summary = "Create a task")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid task title",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Task createTask(@Valid @RequestBody CreateTaskRequest request) {
        return taskMetrics.record(
                "create",
                () -> taskService.createTask(request.title())
        );
    }

    @Operation(summary = "Get a task by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    Task getTask(@Parameter(description = "Task id", example = "1") @PathVariable long id) {
        return taskMetrics.record(
                "get",
                () -> taskService.getTask(id)
        );
    }

    @Operation(summary = "Update a task title")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid task title",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/{id}")
    Task updateTaskTitle(
            @Parameter(description = "Task id", example = "1") @PathVariable long id,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return taskMetrics.record(
                "update",
                () -> taskService.updateTaskTitle(id, request.title())
        );
    }

    @Operation(summary = "Mark a task completed")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task completed"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/{id}/complete")
    Task completeTask(@Parameter(description = "Task id", example = "1") @PathVariable long id) {
        return taskMetrics.record(
                "complete",
                () -> taskService.completeTask(id)
        );
    }

    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTask(@Parameter(description = "Task id", example = "1") @PathVariable long id) {
        taskMetrics.record(
                "delete",
                () -> taskService.deleteTask(id)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleValidationError(MethodArgumentNotValidException exception) {
        recordValidationError(exception);

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

    private void recordValidationError(MethodArgumentNotValidException exception) {
        String methodName = exception.getParameter().getMethod().getName();

        switch (methodName) {
            case "createTask" -> taskMetrics.recordValidationError("create");
            case "updateTaskTitle" -> taskMetrics.recordValidationError("update");
            default -> {
                // No task operation metric is recorded for unrelated validation errors.
            }
        }
    }

    @Schema(description = "Request body for creating a task")
    record CreateTaskRequest(
            @Schema(description = "Task title", example = "Try the task API")
            @NotBlank(message = "Task title must not be blank")
            String title
    ) {
    }

    @Schema(description = "Request body for updating a task title")
    record UpdateTaskRequest(
            @Schema(description = "Task title", example = "Updated task title")
            @NotBlank(message = "Task title must not be blank")
            String title
    ) {
    }

    @Schema(description = "Error response")
    record ErrorResponse(
            @Schema(description = "Error message", example = "Task title must not be blank")
            String message
    ) {
    }
}
