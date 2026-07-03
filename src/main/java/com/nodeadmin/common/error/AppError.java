package com.nodeadmin.common.error;

/**
 * Base runtime exception for all application-level errors.
 * Mirrors NodeAdmin's AppError (src/errors/AppError.ts).
 */
public class AppError extends RuntimeException {

    private final int status;
    private final String code;

    public AppError(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public AppError(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
