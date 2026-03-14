package io.velo.was.deploy;

/**
 * Thrown when WAR deployment fails.
 */
public class DeploymentException extends Exception {

    public DeploymentException(String message) {
        super(message);
    }

    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
