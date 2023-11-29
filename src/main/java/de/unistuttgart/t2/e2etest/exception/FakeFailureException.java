package de.unistuttgart.t2.e2etest.exception;

import java.io.Serial;

/**
 * Indicates a faked failure.
 *
 * @author maumau
 */
public final class FakeFailureException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public FakeFailureException(String message) {
        super(message);
    }
}
