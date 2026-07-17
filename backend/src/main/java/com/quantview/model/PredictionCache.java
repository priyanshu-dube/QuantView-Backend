package com.quantview.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for caching Python ML prediction results.
 * Each ticker has at most one cached entry (UNIQUE on ticker).
 * Entries expire after 15 minutes.
 */
@Entity
@Table(name = "prediction_cache", indexes = {
    @Index(name = "idx_cache_ticker_time", columnList = "ticker, cached_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String ticker;

    /** Full JSON blob from the Python ML engine response. */
    @Column(name = "prediction_json", columnDefinition = "LONGTEXT")
    @Lob
    private String predictionJson;

    @Column(name = "cached_at", nullable = false)
    private LocalDateTime cachedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        cachedAt = LocalDateTime.now();
    }
}
