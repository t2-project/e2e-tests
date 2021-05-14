package de.unistuttgart.t2.e2etest.exception;

/**
 * Indicates a faked failure.
 * 
 * @author maumau
 *
 */
public class FakeFailureException extends Exception {
    
    public FakeFailureException (String message) {
        super(message);
    }
}
