package me.shail.service;

/** Another task generation run is still running; only one runs at a time. */
public class GenerationInProgressException extends RuntimeException {

    public GenerationInProgressException() {
        super("A task generation run is already running");
    }
}
