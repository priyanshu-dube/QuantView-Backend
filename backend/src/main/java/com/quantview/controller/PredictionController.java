package com.quantview.controller;

import com.quantview.dto.ApiResponse;
import com.quantview.dto.StockPredictionDTO;
import com.quantview.service.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/predict/{ticker}
 * 
 * Uses StockService which provides:
 *   - Ticker format validation
 *   - Database-backed prediction cache (15-min TTL)
 *   - Proper error handling with custom exceptions
 */
@RestController
public class PredictionController {

    private final StockService stockService;

    public PredictionController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/api/predict/{ticker}")
    public ResponseEntity<ApiResponse<StockPredictionDTO>> getPrediction(@PathVariable String ticker) {
        System.out.println("[PredictionController] Prediction request for " + ticker);
        try {
            StockPredictionDTO prediction = stockService.getPrediction(ticker);
            return ResponseEntity.ok(ApiResponse.success(prediction));
        } catch (StockService.InvalidTickerException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (StockService.TickerNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (StockService.MlEngineUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
