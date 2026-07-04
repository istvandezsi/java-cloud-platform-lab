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

    const saveButton = document.createElement("button");
    saveButton.type = "button";
    saveButton.className = "task-button";
    saveButton.textContent = "Save";
    saveButton.addEventListener("click", async () => {
        const title = titleInput.value.trim();

        if (!title) {
            setStatus("Error: task title cannot be empty.", "error");
            return;
        }

        try {
            saveButton.disabled = true;
            await requestJson(`/api/tasks/${task.id}`, {
                method: "PATCH",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({title})
            });
            setStatus("Task title updated.", "success");
            await loadTasks();
        } catch (error) {
            setStatus(`Error: ${error.message}`, "error");
        } finally {
            saveButton.disabled = false;
        }
    });

    const completeButton = document.createElement("button");
    completeButton.type = "button";
    completeButton.className = "task-button";
    completeButton.textContent = "Complete";
    completeButton.disabled = task.completed;
    completeButton.addEventListener("click", async () => {
        try {
            completeButton.disabled = true;
            await requestJson(`/api/tasks/${task.id}/complete`, {method: "PATCH"});
            setStatus("Task marked completed.", "success");
            await loadTasks();
        } catch (error) {
            setStatus(`Error: ${error.message}`, "error");
            completeButton.disabled = false;
        }
    });

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.className = "danger-button";
    deleteButton.textContent = "Delete";
    deleteButton.addEventListener("click", async () => {
        try {
            deleteButton.disabled = true;
            await requestJson(`/api/tasks/${task.id}`, {method: "DELETE"});
            setStatus("Task deleted.", "success");
            await loadTasks();
        } catch (error) {
            setStatus(`Error: ${error.message}`, "error");
            deleteButton.disabled = false;
        }
    });

    actions.append(saveButton, completeButton, deleteButton);
    item.append(state, titleInput, actions);
    return item;
}

async function loadTasks() {
    try {
        refreshButton.disabled = true;
        const tasks = await requestJson("/api/tasks");

        taskList.replaceChildren(...tasks.map(createTaskElement));
        emptyState.hidden = tasks.length > 0;
        updateSummary(tasks);
    } catch (error) {
        setStatus(`Error: ${error.message}`, "error");
    } finally {
        refreshButton.disabled = false;
    }
}

taskForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const title = taskTitleInput.value.trim();

    if (!title) {
        setStatus("Error: task title cannot be empty.", "error");
        return;
    }

    try {
        await requestJson("/api/tasks", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({title})
        });

        taskTitleInput.value = "";
        taskTitleInput.focus();
        setStatus("Task created.", "success");
        await loadTasks();
    } catch (error) {
        setStatus(`Error: ${error.message}`, "error");
    }
});

refreshButton.addEventListener("click", loadTasks);

loadTasks();
