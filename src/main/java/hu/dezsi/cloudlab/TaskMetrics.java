package hu.dezsi.cloudlab;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class TaskMetrics {

    static final String METRIC_NAME = "cloudlab.task.api.operations";

    private final MeterRegistry meterRegistry;

    public TaskMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T record(String operation, Supplier<T> action) {
        try {
            T result = action.get();
            increment(operation, "success");
            return result;
        } catch (TaskNotFoundException exception) {
            increment(operation, "not_found");
            throw exception;
        }
    }

    public void record(String operation, Runnable action) {
        record(operation, () -> {
            action.run();
            return null;
        });
    }

    public void recordValidationError(String operation) {
        increment(operation, "validation_error");
    }

    private void increment(String operation, String outcome) {
        meterRegistry.counter(
                METRIC_NAME,
                "operation", operation,
                "outcome", outcome
        ).increment();
    }
}
