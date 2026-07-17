package com.quantview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantview.dto.StockPredictionDTO;
import com.quantview.model.PredictionCache;
import com.quantview.repository.PredictionCacheRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service layer for stock predictions.
 *
 * Responsibilities:
 *   1. Validate ticker format
 *   2. Check prediction cache (15-min TTL)
 *   3. Call Python ML engine if cache miss/expired
 *   4. Save fresh predictions to cache
 *   5. Deserialize Python response into StockPredictionDTO
 */
@Service
public class StockService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PredictionCacheRepository cacheRepository;
    private final String pythonServiceUrl;

    /** Cache time-to-live in minutes. */
    private static final long CACHE_TTL_MINUTES = 15;

    /** Ticker must be 1-20 chars: alphanumeric, dots, carets, hyphens. */
    private static final Pattern VALID_TICKER = Pattern.compile("^[A-Za-z0-9.^-]{1,20}$");

    public StockService(
            @Value("${python.service.url:http://localhost:5000}") String pythonServiceUrl,
            PredictionCacheRepository cacheRepository) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.cacheRepository = cacheRepository;
        this.pythonServiceUrl = pythonServiceUrl;
    }

    /**
     * Get a stock prediction, serving from cache if fresh.
     *
     * @param ticker Stock ticker symbol (e.g., "AAPL", "RELIANCE.NS")
     * @return StockPredictionDTO with the prediction results
     * @throws InvalidTickerException if ticker format is invalid
     * @throws TickerNotFoundException if Python returns 404
     * @throws MlEngineUnavailableException if Python is unreachable
     */
    public StockPredictionDTO getPrediction(String ticker) {
        String cleanTicker = ticker.trim().toUpperCase();

        // 1. Validate ticker format
        if (!VALID_TICKER.matcher(cleanTicker).matches()) {
            throw new InvalidTickerException("Invalid ticker format: " + ticker);
        }

        // 2. Check cache
        Optional<PredictionCache> cached = cacheRepository.findByTicker(cleanTicker);
        if (cached.isPresent()) {
            PredictionCache entry = cached.get();
            long minutesAgo = ChronoUnit.MINUTES.between(entry.getCachedAt(), LocalDateTime.now());

            if (minutesAgo < CACHE_TTL_MINUTES) {
                System.out.println("[StockService] Cache HIT for " + cleanTicker
                        + " (cached " + minutesAgo + "m ago)");
                return deserializeJson(entry.getPredictionJson());
            } else {
                System.out.println("[StockService] Cache EXPIRED for " + cleanTicker
                        + " (cached " + minutesAgo + "m ago)");
            }
        } else {
            System.out.println("[StockService] Cache MISS for " + cleanTicker);
        }

        // 3. Call Python ML engine
        String url = pythonServiceUrl + "/predict?ticker=" + cleanTicker;
        System.out.println("[StockService] Calling Python: " + url);

        String responseJson;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            responseJson = response.getBody();
            System.out.println("[StockService] Python responded: " + response.getStatusCode());

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new TickerNotFoundException(
                        "Ticker not found or no data available: " + cleanTicker);
            }
            throw new MlEngineUnavailableException(
                    "Python ML engine returned error: " + e.getStatusCode());

        } catch (ResourceAccessException e) {
            throw new MlEngineUnavailableException(
                    "ML engine unavailable. Ensure Python service is running on " + pythonServiceUrl);

        } catch (Exception e) {
            throw new MlEngineUnavailableException(
                    "Unexpected error calling ML engine: " + e.getMessage());
        }

        // 4. Save to cache (upsert)
        try {
            PredictionCache cacheEntry = cached.orElse(new PredictionCache());
            cacheEntry.setTicker(cleanTicker);
            cacheEntry.setPredictionJson(responseJson);
            cacheEntry.setCachedAt(LocalDateTime.now());
            cacheRepository.save(cacheEntry);
            System.out.println("[StockService] Cached prediction for " + cleanTicker);
        } catch (Exception e) {
            // Cache write failure is non-fatal — log and continue
            System.out.println("[StockService] WARNING: Failed to cache prediction: " + e.getMessage());
        }

        // 5. Deserialize and return
        return deserializeJson(responseJson);
    }

    /**
     * Proxy a raw prediction call to Python (backward compatibility for /api/stocks/analyze).
     * Returns the raw JSON string from Python without deserialization.
     */
    public ResponseEntity<String> fetchPredictionRaw(String ticker) {
        String cleanTicker = ticker.trim().toUpperCase();

        if (!VALID_TICKER.matcher(cleanTicker).matches()) {
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"Invalid ticker format\"}");
        }

        String url = pythonServiceUrl + "/predict?ticker=" + cleanTicker;
        System.out.println("[StockService] Raw proxy to: " + url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsString());

        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\": \"ML Engine unavailable\"}");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
        }
    }

    /** Deserialize JSON string into StockPredictionDTO. */
    private StockPredictionDTO deserializeJson(String json) {
        try {
            return objectMapper.readValue(json, StockPredictionDTO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse prediction JSON: " + e.getMessage(), e);
        }
    }

    // ── Custom exceptions ──

    public static class InvalidTickerException extends RuntimeException {
        public InvalidTickerException(String message) { super(message); }
    }

    public static class TickerNotFoundException extends RuntimeException {
        public TickerNotFoundException(String message) { super(message); }
    }

    public static class MlEngineUnavailableException extends RuntimeException {
        public MlEngineUnavailableException(String message) { super(message); }
    }
}
