package me.shail.service;

/** A task change that is not allowed; {@link #conflict()} when the task's state forbids it (e.g. already closed). */
public class InvalidTaskChangeException extends RuntimeException {

    private final boolean conflict;

    public InvalidTaskChangeException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean conflict() {
        return conflict;
    }
}
