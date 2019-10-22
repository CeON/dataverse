package edu.harvard.iq.dataverse.engine.command.exception;

import javax.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class UpdateFailedException extends RuntimeException {

    public UpdateFailedException(String message) {
        super(message);
    }

    public UpdateFailedException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
