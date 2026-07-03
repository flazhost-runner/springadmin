package com.nodeadmin.common.error;

/**
 * Thrown when a requested resource does not exist (HTTP 404).
 */
public class NotFoundError extends AppError {

    public NotFoundError(String message) {
        super(404, "NOT_FOUND", message);
    }

    public NotFoundError(String message, Throwable cause) {
        super(404, "NOT_FOUND", message, cause);
    }
}
