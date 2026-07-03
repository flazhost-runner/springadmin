package com.nodeadmin.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for building consistent JSON response envelopes.
 *
 * <p>All responses share the shape:
 * <pre>
 * { "status": &lt;bool&gt;, "message": "...", "data": ... }
 * </pre>
 *
 * <p>Mirrors NodeAdmin's ResponseHandler pattern where every route handler
 * returns a uniform envelope so clients never need to special-case shapes.
 */
public final class ResponseHandler {

    private ResponseHandler() {
        // utility class — no instantiation
    }

    /**
     * Success response with a data payload (HTTP 200).
     */
    public static ResponseEntity<Map<String, Object>> success(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", true);
        body.put("message", message);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    /**
     * Success response without a data payload (HTTP 200).
     */
    public static ResponseEntity<Map<String, Object>> success(String message) {
        return success(message, null);
    }

    /**
     * Error response with an explicit HTTP status code.
     */
    public static ResponseEntity<Map<String, Object>> error(String message, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", false);
        body.put("message", message);
        body.put("data", null);
        return ResponseEntity.status(status).body(body);
    }
}
