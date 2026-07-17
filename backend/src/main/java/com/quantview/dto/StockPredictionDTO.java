package com.quantview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO mirroring the full Python ML engine JSON response.
 * Used for deserialization of the Python /predict response
 * and for the spec-compliant API output.
 *
 * Jackson ignores unknown properties so this is forward-compatible
 * if the Python response grows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockPredictionDTO {

    // === Spec fields ===
    private String ticker;
    private Double currentPrice;
    private Double predictedPrice;
    private Double priceChange;
    private Double priceChangePercent;
    private Double mse;
    private Double mae;
    private Double r2Score;
    private Map<String, Double> feature_importances;
    private List<HistoricalDataPoint> historicalData;
    private Map<String, Double> regressionCoefficients;
    private Integer dataPoints;
    private Integer trainingSize;
    private String lastUpdated;
    private String modelUsed;

    // === Frontend-compatible fields (proxied through) ===
    private List<Map<String, Object>> historical_prices;
    private List<Map<String, Object>> predicted_prices;
    private List<Map<String, Object>> sma_20;
    private List<Map<String, Object>> ema_20;
    private Double next_day_prediction;
    private Double r2_score;

    /**
     * Single data point in the historicalData array.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoricalDataPoint {
        private String date;
        private Double actual;
        private Double predicted;
        private Double sma50;
        private Double sma200;
    }
}
