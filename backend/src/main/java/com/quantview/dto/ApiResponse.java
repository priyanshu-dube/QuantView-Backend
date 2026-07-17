package com.quantview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic API response wrapper.
 * All endpoints return this shape:
 *   Success: { "success": true,  "data": {...} }
 *   Error:   { "success": false, "error": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String error;
    private String timestamp;

    private ApiResponse() {
        this.timestamp = java.time.Instant.now().toString();
    }

    /** Factory: create a success response with data payload. */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    /** Factory: create an error response with message. */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error = message;
        return r;
    }

    // ── Getters (Jackson serialization) ──

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getError() { return error; }
    public String getTimestamp() { return timestamp; }
}
