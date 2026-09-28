package me.shail.rules;

import java.util.List;

/**
 * A care program file that failed validation. {@link #errors()} lists every problem, each with its location.
 */
public class ProgramDefinitionException extends RuntimeException {

    private final List<String> errors;

    public ProgramDefinitionException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
