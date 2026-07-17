package com.quantview.exception;

import com.quantview.dto.ApiResponse;
import com.quantview.service.StockService;
import com.quantview.service.WatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Intercepts all unhandled exceptions across Spring Boot controllers
 * and packages them cleanly into the standard ApiResponse format.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParams(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Missing required parameter: " + ex.getParameterName()));
    }

    // StockService custom exceptions
    @ExceptionHandler(StockService.InvalidTickerException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTicker(StockService.InvalidTickerException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(StockService.TickerNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTickerNotFound(StockService.TickerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(StockService.MlEngineUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMlUnavailable(StockService.MlEngineUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ex.getMessage()));
    }

    // WatchlistService custom exceptions
    @ExceptionHandler(WatchlistService.DuplicateTickerException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateTicker(WatchlistService.DuplicateTickerException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(WatchlistService.TickerNotInWatchlistException.class)
    public ResponseEntity<ApiResponse<Void>> handleTickerNotInWatchlist(WatchlistService.TickerNotInWatchlistException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpClientError(HttpClientErrorException ex) {
        String body = ex.getResponseBodyAsString();
        String errorMsg = body;
        try {
            if (body.contains("\"error\"")) {
                int start = body.indexOf("\"error\"") + 10;
                int end = body.indexOf("\"", start);
                errorMsg = body.substring(start, end);
            }
        } catch (Exception ignored) {}
        
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error(errorMsg));
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpServerError(HttpServerErrorException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error("Downstream service error"));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceAccessError(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Service temporarily unavailable. Check connections."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected internal error occurred."));
    }
}
