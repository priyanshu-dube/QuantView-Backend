package com.quantview.controller;

import com.quantview.dto.ApiResponse;
import com.quantview.dto.WatchlistItemDTO;
import com.quantview.service.WatchlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for watchlist CRUD operations.
 *
 * User identification is via the X-User-Id request header (Clerk user ID).
 *
 * Endpoints:
 *   GET    /api/watchlist              — fetch all watchlist items for user
 *   POST   /api/watchlist              — add ticker (body: { "ticker": "AAPL" })
 *   DELETE /api/watchlist/{ticker}     — remove ticker from watchlist
 */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * GET /api/watchlist
     * Returns all watchlist items for the user identified by X-User-Id header.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WatchlistItemDTO>>> getWatchlist(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing X-User-Id header"));
        }

        List<WatchlistItemDTO> items = watchlistService.getWatchlist(userId);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    /**
     * POST /api/watchlist
     * Adds a ticker to the user's watchlist.
     * Body: { "ticker": "AAPL" }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<WatchlistItemDTO>> addToWatchlist(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, String> body) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing X-User-Id header"));
        }

        String ticker = body.get("ticker");
        if (ticker == null || ticker.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing 'ticker' in request body"));
        }

        try {
            WatchlistItemDTO item = watchlistService.addTicker(userId, ticker);
            return ResponseEntity.ok(ApiResponse.success(item));

        } catch (WatchlistService.DuplicateTickerException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * DELETE /api/watchlist/{ticker}
     * Removes a ticker from the user's watchlist.
     */
    @DeleteMapping("/{ticker}")
    public ResponseEntity<ApiResponse<String>> removeFromWatchlist(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String ticker) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing X-User-Id header"));
        }

        try {
            watchlistService.removeTicker(userId, ticker);
            return ResponseEntity.ok(
                    ApiResponse.success("Removed " + ticker.toUpperCase() + " from watchlist"));

        } catch (WatchlistService.TickerNotInWatchlistException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
