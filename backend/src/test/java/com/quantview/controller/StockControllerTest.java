package com.quantview.controller;

import com.quantview.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for StockController.
 * Uses MockRestServiceServer to mock the Python ML engine HTTP calls.
 * Uses H2 in-memory DB (test profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockService stockService;

    private MockRestServiceServer mockPythonServer;

    /** Sample Python /predict response JSON for testing. */
    private static final String SAMPLE_PYTHON_RESPONSE = """
            {
              "ticker": "AAPL",
              "currentPrice": 213.45,
              "predictedPrice": 215.20,
              "priceChange": 1.75,
              "priceChangePercent": 0.82,
              "mse": 12.43,
              "r2Score": 0.94,
              "historicalData": [
                { "date": "2024-01-15", "actual": 185.20, "predicted": 184.90, "sma50": 182.10, "sma200": 175.30 }
              ],
              "regressionCoefficients": { "slope": 0.045, "intercept": -8234.12 },
              "dataPoints": 504,
              "trainingSize": 403,
              "lastUpdated": "2025-07-10T10:30:00",
              "modelUsed": "XGBRegressor",
              "historical_prices": [
                { "date": "2024-01-15", "open": 185.0, "high": 186.5, "low": 184.0, "close": 185.20, "volume": 50000000 }
              ],
              "predicted_prices": [
                { "date": "2024-01-15", "value": 184.90 }
              ],
              "sma_20": [{ "date": "2024-01-15", "value": 183.50 }],
              "ema_20": [{ "date": "2024-01-15", "value": 183.80 }],
              "next_day_prediction": 215.20,
              "r2_score": 0.94
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        // Extract the RestTemplate from StockService via reflection to attach MockRestServiceServer
        Field rtField = StockService.class.getDeclaredField("restTemplate");
        rtField.setAccessible(true);
        RestTemplate restTemplate = (RestTemplate) rtField.get(stockService);
        mockPythonServer = MockRestServiceServer.createServer(restTemplate);
    }

    // ── /api/stock/predict tests ──

    @Test
    void predict_validTicker_returnsWrappedSuccess() throws Exception {
        mockPythonServer.expect(ExpectedCount.once(),
                        requestTo("http://localhost:5000/predict?ticker=AAPL"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_PYTHON_RESPONSE, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/stock/predict").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticker").value("AAPL"))
                .andExpect(jsonPath("$.data.currentPrice").value(213.45))
                .andExpect(jsonPath("$.data.predictedPrice").value(215.20))
                .andExpect(jsonPath("$.data.mse").value(12.43))
                .andExpect(jsonPath("$.data.r2Score").value(0.94))
                .andExpect(jsonPath("$.data.regressionCoefficients.slope").value(0.045))
                .andExpect(jsonPath("$.data.dataPoints").value(504))
                .andExpect(jsonPath("$.data.trainingSize").value(403));

        mockPythonServer.verify();
    }

    @Test
    void predict_missingTicker_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/stock/predict").param("ticker", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void predict_invalidTickerFormat_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/stock/predict").param("ticker", "!!!INVALID!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error", containsString("Invalid ticker")));
    }

    // ── /api/stocks/analyze tests (backward compat) ──

    @Test
    void analyze_validTicker_returnsRawJson() throws Exception {
        mockPythonServer.expect(ExpectedCount.once(),
                        requestTo("http://localhost:5000/predict?ticker=MSFT"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_PYTHON_RESPONSE, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/stocks/analyze").param("ticker", "MSFT"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockPythonServer.verify();
    }

    // ── /api/health tests ──

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ok"));
    }
}
