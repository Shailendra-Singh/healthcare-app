package me.shail.service;

/** The rules-engine has no successful evaluation to generate tasks from yet. */
public class NoEvaluationException extends RuntimeException {

    public NoEvaluationException() {
        super("The rules-engine has no successful evaluation yet");
    }
}
