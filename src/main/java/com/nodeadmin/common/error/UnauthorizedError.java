package com.nodeadmin.common.error;

/**
 * Thrown when a request lacks valid authentication credentials (HTTP 401).
 */
public class UnauthorizedError extends AppError {

    public UnauthorizedError(String message) {
        super(401, "UNAUTHORIZED", message);
    }

    public UnauthorizedError(String message, Throwable cause) {
        super(401, "UNAUTHORIZED", message, cause);
    }
}
