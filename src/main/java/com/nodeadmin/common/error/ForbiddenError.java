package com.nodeadmin.common.error;

/**
 * Thrown when an authenticated user lacks permission for the requested action (HTTP 403).
 */
public class ForbiddenError extends AppError {

    public ForbiddenError(String message) {
        super(403, "FORBIDDEN", message);
    }

    public ForbiddenError(String message, Throwable cause) {
        super(403, "FORBIDDEN", message, cause);
    }
}
