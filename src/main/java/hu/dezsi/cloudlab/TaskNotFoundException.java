package hu.dezsi.cloudlab;

class TaskNotFoundException extends RuntimeException {

    TaskNotFoundException(long id) {
        super("Task not found: " + id);
    }
}