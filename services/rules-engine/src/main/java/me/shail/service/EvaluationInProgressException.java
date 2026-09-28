package me.shail.service;

/** Another evaluation is still running; only one runs at a time. */
public class EvaluationInProgressException extends RuntimeException {

    public EvaluationInProgressException() {
        super("An evaluation is already running");
    }
}
