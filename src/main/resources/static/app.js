const taskForm = document.querySelector("#task-form");
const taskTitleInput = document.querySelector("#task-title");
const taskList = document.querySelector("#task-list");
const emptyState = document.querySelector("#empty-state");
const statusMessage = document.querySelector("#status-message");
const refreshButton = document.querySelector("#refresh-button");
const totalCount = document.querySelector("#total-count");
const openCount = document.querySelector("#open-count");
const completedCount = document.querySelector("#completed-count");

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);

    if (!response.ok) {
        let message = `Request failed with status ${response.status}`;

        try {
            const error = await response.json();
            message = error.message || message;
        } catch {
            // Keep the generic message when the response has no JSON body.
        }

        throw new Error(message);
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

function listTasks() {
    return requestJson("/api/tasks");
}

function createTask(title) {
    return requestJson("/api/tasks", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({title})
    });
}

function updateTaskTitle(taskId, title) {
    return requestJson(`/api/tasks/${taskId}`, {
        method: "PATCH",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({title})
    });
}

function completeTask(taskId) {
    return requestJson(`/api/tasks/${taskId}/complete`, {
        method: "PATCH"
    });
}

function deleteTask(taskId) {
    return requestJson(`/api/tasks/${taskId}`, {
        method: "DELETE"
    });
}

function setStatus(message, type = "") {
    statusMessage.textContent = message;
    statusMessage.className = `status-message ${type}`.trim();
}

function updateSummary(tasks) {
    const completed = tasks.filter((task) => task.completed).length;

    totalCount.textContent = tasks.length;
    openCount.textContent = tasks.length - completed;
    completedCount.textContent = completed;
}

function renderTasks(tasks) {
    taskList.replaceChildren(...tasks.map(createTaskElement));
    emptyState.hidden = tasks.length > 0;
    updateSummary(tasks);
}

function createTaskElement(task) {
    const item = document.createElement("li");
    item.className = task.completed ? "task-item completed" : "task-item";

    const state = document.createElement("span");
    state.className = task.completed ? "task-state completed" : "task-state";
    state.textContent = task.completed ? "Done" : "Open";

    const titleInput = document.createElement("input");
    titleInput.type = "text";
    titleInput.value = task.title;
    titleInput.setAttribute("aria-label", `Title for task ${task.id}`);

    const actions = document.createElement("div");
    actions.className = "task-actions";

    const saveButton = createButton("Save", "task-button", async () => {
        await saveTaskTitle(task.id, titleInput, saveButton);
    });

    const completeButton = createButton("Complete", "task-button", async () => {
        await completeExistingTask(task.id, completeButton);
    });

    completeButton.disabled = task.completed;

    const deleteButton = createButton("Delete", "danger-button", async () => {
        await deleteExistingTask(task.id, deleteButton);
    });

    actions.append(saveButton, completeButton, deleteButton);
    item.append(state, titleInput, actions);

    return item;
}

function createButton(text, className, onClick) {
    const button = document.createElement("button");

    button.type = "button";
    button.className = className;
    button.textContent = text;
    button.addEventListener("click", onClick);

    return button;
}

async function loadTasks() {
    try {
        refreshButton.disabled = true;

        const tasks = await listTasks();
        renderTasks(tasks);
    } catch (error) {
        setStatus(`Error: ${error.message}`, "error");
    } finally {
        refreshButton.disabled = false;
    }
}

async function handleCreateTask(event) {
    event.preventDefault();

    const title = taskTitleInput.value.trim();

    if (!title) {
        setStatus("Error: task title cannot be empty.", "error");
        return;
    }

    try {
        await createTask(title);

        taskTitleInput.value = "";
        taskTitleInput.focus();

        setStatus("Task created.", "success");
        await loadTasks();
    } catch (error) {
        setStatus(`Error: ${error.message}`, "error");
    }
}

async function saveTaskTitle(taskId, titleInput, saveButton) {
    const title = titleInput.value.trim();

    if (!title) {
        setStatus("Error: task title cannot be empty.", "error");
        return;
    }

    await runTaskAction(saveButton, "Task title updated.", async () => {
        await updateTaskTitle(taskId, title);
    });
}

async function completeExistingTask(taskId, completeButton) {
    await runTaskAction(completeButton, "Task marked completed.", async () => {
        await completeTask(taskId);
    });
}

async function deleteExistingTask(taskId, deleteButton) {
    await runTaskAction(deleteButton, "Task deleted.", async () => {
        await deleteTask(taskId);
    });
}

async function runTaskAction(button, successMessage, action) {
    try {
        button.disabled = true;

        await action();

        setStatus(successMessage, "success");
        await loadTasks();
    } catch (error) {
        setStatus(`Error: ${error.message}`, "error");
    } finally {
        button.disabled = false;
    }
}

taskForm.addEventListener("submit", handleCreateTask);
refreshButton.addEventListener("click", loadTasks);

loadTasks();
