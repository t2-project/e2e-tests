package de.unistuttgart.t2.e2etest.exception;

/**
 * Indicates a faked failure.
 *
 * @author maumau
 */
public class FakeFailureException extends Exception {

    private static final long serialVersionUID = 1L;

    public FakeFailureException(String message) {
        super(message);
    }
}
