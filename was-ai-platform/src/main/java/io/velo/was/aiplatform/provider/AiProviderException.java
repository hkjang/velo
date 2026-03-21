package io.velo.was.aiplatform.provider;

/**
 * Exception thrown when an AI provider call fails. Contains the provider ID
 * and HTTP status code (if applicable) for failover decision making.
 */
public class AiProviderException extends RuntimeException {

    private final String providerId;
    private final int statusCode;

    public AiProviderException(String providerId, String message) {
        this(providerId, 0, message, null);
    }

    public AiProviderException(String providerId, int statusCode, String message) {
        this(providerId, statusCode, message, null);
    }

    public AiProviderException(String providerId, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.statusCode = statusCode;
    }

    public String getProviderId() {
        return providerId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503;
    }
}
