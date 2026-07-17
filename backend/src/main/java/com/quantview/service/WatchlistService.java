package com.quantview.service;

import com.quantview.dto.WatchlistItemDTO;
import com.quantview.model.Watchlist;
import com.quantview.repository.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for watchlist CRUD operations.
 * Handles business logic and entity-to-DTO conversion.
 */
@Service
public class WatchlistService {

    private final WatchlistRepository repository;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public WatchlistService(WatchlistRepository repository) {
        this.repository = repository;
    }

    /**
     * Get all watchlist items for a user.
     *
     * @param userId Clerk user ID from X-User-Id header
     * @return List of WatchlistItemDTO
     */
    public List<WatchlistItemDTO> getWatchlist(String userId) {
        return repository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Add a ticker to the user's watchlist.
     *
     * @param userId Clerk user ID
     * @param ticker Stock ticker symbol
     * @return The created WatchlistItemDTO
     * @throws DuplicateTickerException if ticker already in watchlist
     */
    public WatchlistItemDTO addTicker(String userId, String ticker) {
        String cleanTicker = ticker.trim().toUpperCase();

        if (repository.existsByUserIdAndTicker(userId, cleanTicker)) {
            throw new DuplicateTickerException("Ticker '" + cleanTicker + "' already in watchlist");
        }

        Watchlist item = new Watchlist();
        item.setUserId(userId);
        item.setTicker(cleanTicker);

        Watchlist saved = repository.save(item);
        System.out.println("[WatchlistService] Added " + cleanTicker + " for user " + userId);

        return toDTO(saved);
    }

    /**
     * Remove a ticker from the user's watchlist.
     *
     * @param userId Clerk user ID
     * @param ticker Stock ticker symbol
     * @throws TickerNotInWatchlistException if ticker not found in user's watchlist
     */
    public void removeTicker(String userId, String ticker) {
        String cleanTicker = ticker.trim().toUpperCase();

        List<Watchlist> items = repository.findByUserId(userId);
        Watchlist target = items.stream()
                .filter(w -> w.getTicker().equals(cleanTicker))
                .findFirst()
                .orElseThrow(() -> new TickerNotInWatchlistException(
                        "Ticker '" + cleanTicker + "' not found in watchlist"));

        repository.delete(target);
        System.out.println("[WatchlistService] Removed " + cleanTicker + " for user " + userId);
    }

    /** Convert Watchlist entity to DTO. */
    private WatchlistItemDTO toDTO(Watchlist entity) {
        return new WatchlistItemDTO(
                entity.getId(),
                entity.getUserId(),
                entity.getTicker(),
                entity.getAddedAt() != null ? entity.getAddedAt().format(DATETIME_FMT) : null
        );
    }

    // ── Custom exceptions ──

    public static class DuplicateTickerException extends RuntimeException {
        public DuplicateTickerException(String message) { super(message); }
    }

    public static class TickerNotInWatchlistException extends RuntimeException {
        public TickerNotInWatchlistException(String message) { super(message); }
    }
}
