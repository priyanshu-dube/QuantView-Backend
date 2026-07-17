package com.quantview.repository;

import com.quantview.model.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Watchlist CRUD operations.
 */
@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserId(String userId);

    boolean existsByUserIdAndTicker(String userId, String ticker);
}
