package com.quantview.util;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility to check if a specific market/exchange is currently open
 * based on regional trading hours.
 */
@Component
public class MarketHoursUtil {

    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    private static final ZoneId ASIA_KOLKATA = ZoneId.of("Asia/Kolkata");

    // US Market Hours: 9:30 AM to 4:00 PM Eastern Time
    private static final LocalTime US_OPEN = LocalTime.of(9, 30);
    private static final LocalTime US_CLOSE = LocalTime.of(16, 0);

    // India Market Hours: 9:15 AM to 3:30 PM Indian Standard Time
    private static final LocalTime IN_OPEN = LocalTime.of(9, 15);
    private static final LocalTime IN_CLOSE = LocalTime.of(15, 30);

    /**
     * Determine if a market is officially open right now.
     * Crypto is always open 24/7.
     */
    public boolean isMarketOpen(String exchange) {
        if (exchange == null) return false;

        String upper = exchange.toUpperCase();
        if (upper.equals("CRYPTO")) return true;

        if (upper.contains("NSE") || upper.contains("BSE")) {
            return isIndiaMarketOpen();
        }

        // Default layout for NYSE / NASDAQ / US Equities
        return isUSMarketOpen();
    }

    private boolean isUSMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        DayOfWeek day = now.getDayOfWeek();
        
        // Weekend check
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        
        LocalTime time = now.toLocalTime();
        return !time.isBefore(US_OPEN) && !time.isAfter(US_CLOSE);
    }

    private boolean isIndiaMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(ASIA_KOLKATA);
        DayOfWeek day = now.getDayOfWeek();
        
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        
        LocalTime time = now.toLocalTime();
        return !time.isBefore(IN_OPEN) && !time.isAfter(IN_CLOSE);
    }
}
