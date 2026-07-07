package hu.dezsi.cloudlab;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Task item")
public record Task(
        @Schema(description = "Task id", example = "1")
        long id,

        @Schema(description = "Task title", example = "Try the task API")
        String title,

        @Schema(description = "Whether the task is completed", example = "false")
        boolean completed
) {
}
