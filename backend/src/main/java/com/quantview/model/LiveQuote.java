package com.quantview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a real-time live market quote.
 * Updates flow in rapidly from Finnhub WS or yfinance fallback.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveQuote {

    private String symbol;
    private String companyName;
    private String exchange;      // "NSE", "NYSE", "NASDAQ", "CRYPTO"
    private String sector;

    // Financial Data
    private Double price;
    private Double prevClose;
    private Double change;
    private Double changePercent;
    
    private Double dayHigh;
    private Double dayLow;
    private Long volume;
    private Long timestamp;       // Unix ms

    // Status
    private Boolean isMarketOpen;
    private String currency;      // "USD", "INR"
}
