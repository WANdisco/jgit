package org.eclipse.jgit.errors;

import java.io.IOException;

/**
 * Exception thrown when server error takes places in GitMS whilst trying to deploy repository
 */
public class RepositoryDeploymentException extends IOException {

    /**
     * Constructs an RepositoryDeploymentException with the specified detail
     * message.
     * @param message
     */
    public RepositoryDeploymentException(String message) {
        super(message);
    }

    /**
     * Constructs an RepositoryDeploymentException with the specified detail
     * message and cause.
     * @param message
     * @param cause
     */
    public RepositoryDeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}