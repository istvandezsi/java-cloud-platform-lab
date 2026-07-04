package hu.dezsi.cloudlab;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TaskService {

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<Long, Task> tasks = new ConcurrentHashMap<>();

    public List<Task> listTasks() {
        return tasks.values().stream()
                .sorted(Comparator.comparingLong(Task::id))
                .toList();
    }

    public Task createTask(String title) {
        long id = sequence.incrementAndGet();
        Task task = new Task(id, title, false);
        tasks.put(id, task);
        return task;
    }

    public Task getTask(long id) {
        Task task = tasks.get(id);

        if (task == null) {
            throw new TaskNotFoundException(id);
        }

        return task;
    }

    public Task updateTaskTitle(long id, String title) {
        Task existing = getTask(id);
        Task updated = new Task(existing.id(), title, existing.completed());
        tasks.put(id, updated);
        return updated;
    }

    public Task completeTask(long id) {
        Task existing = getTask(id);
        Task completed = new Task(existing.id(), existing.title(), true);
        tasks.put(id, completed);
        return completed;
    }

    public void deleteTask(long id) {
        Task removed = tasks.remove(id);

        if (removed == null) {
            throw new TaskNotFoundException(id);
        }
    }
}
