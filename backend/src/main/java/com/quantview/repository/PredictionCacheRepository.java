package com.quantview.repository;

import com.quantview.model.PredictionCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for prediction cache CRUD operations.
 */
@Repository
public interface PredictionCacheRepository extends JpaRepository<PredictionCache, Long> {

    /** Find the cached prediction for a specific ticker. */
    Optional<PredictionCache> findByTicker(String ticker);

    /** Delete the cached prediction for a specific ticker. */
    void deleteByTicker(String ticker);
}
