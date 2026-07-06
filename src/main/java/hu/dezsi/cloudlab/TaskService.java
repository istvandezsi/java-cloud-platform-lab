package hu.dezsi.cloudlab;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private final JdbcClient jdbcClient;
    private final SimpleJdbcInsert insertTask;

    public TaskService(JdbcClient jdbcClient, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.insertTask = new SimpleJdbcInsert(dataSource)
                .withTableName("tasks")
                .usingColumns("title", "completed")
                .usingGeneratedKeyColumns("id");
    }

    public List<Task> listTasks() {
        return jdbcClient.sql("""
                        SELECT id, title, completed
                        FROM tasks
                        ORDER BY id
                        """)
                .query(this::mapTask)
                .list();
    }

    public Task createTask(String title) {
        Number id = insertTask.executeAndReturnKey(Map.of(
                "title", title,
                "completed", false
        ));

        return findTask(id.longValue());
    }

    public Task getTask(long id) {
        return findTask(id);
    }

    public Task updateTaskTitle(long id, String title) {
        int updatedRows = jdbcClient.sql("""
                        UPDATE tasks
                        SET title = :title
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("title", title)
                .update();

        if (updatedRows == 0) {
            throw new TaskNotFoundException(id);
        }

        return findTask(id);
    }

    public Task completeTask(long id) {
        int updatedRows = jdbcClient.sql("""
                        UPDATE tasks
                        SET completed = TRUE
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();

        if (updatedRows == 0) {
            throw new TaskNotFoundException(id);
        }

        return findTask(id);
    }

    public void deleteTask(long id) {
        int deletedRows = jdbcClient.sql("""
                        DELETE FROM tasks
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();

        if (deletedRows == 0) {
            throw new TaskNotFoundException(id);
        }
    }

    private Task findTask(long id) {
        return jdbcClient.sql("""
                        SELECT id, title, completed
                        FROM tasks
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapTask)
                .optional()
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private Task mapTask(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Task(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getBoolean("completed")
        );
    }
}
