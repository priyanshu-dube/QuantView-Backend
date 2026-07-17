package com.quantview.controller;

import com.quantview.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
public class StockController {

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl;

    public StockController(
            @Value("${python.service.url:http://localhost:5000}") String pythonServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.pythonServiceUrl = pythonServiceUrl;
    }

    /**
     * GET /api/stock/{ticker}
     * Caches for 60s. Forwards request to Python Flask.
     */
    @Cacheable(value = "stocks", key = "#ticker.toUpperCase()")
    @GetMapping("/api/stock/{ticker}")
    public ResponseEntity<ApiResponse<Object>> getStock(@PathVariable String ticker) {
        System.out.println("[StockController] Forwarding GET /stock for " + ticker);
        String url = pythonServiceUrl + "/stock?ticker=" + ticker.toUpperCase();
        
        Object pythonResponse = restTemplate.getForObject(url, Object.class);
        return ResponseEntity.ok(ApiResponse.success(pythonResponse));
    }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Object>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }
}
