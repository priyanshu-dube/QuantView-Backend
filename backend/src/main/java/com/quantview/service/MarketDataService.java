package com.quantview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantview.model.LiveQuote;
import com.quantview.util.MarketHoursUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that maintains an in-memory cache of live quotes
 * pushed by Finnhub or pulled by Python Market Poller fallback.
 */
@Service
public class MarketDataService {

    // Thread-safe map holding the latest quote for each ticker
    private final ConcurrentHashMap<String, LiveQuote> quotesCache = new ConcurrentHashMap<>();
    
    // The master list of symbols loaded from market-universe.json
    private final List<String> trackedSymbols = new ArrayList<>();
    
    private final MarketHoursUtil marketHoursUtil;

    public MarketDataService(MarketHoursUtil marketHoursUtil) {
        this.marketHoursUtil = marketHoursUtil;
    }

    @PostConstruct
    public void init() {
        loadUniverse();
    }

    private void loadUniverse() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getResourceAsStream("/market-universe.json");
            if (is != null) {
                JsonNode root = mapper.readTree(is);
                
                // Helper to extract fields recursively
                extractSymbols(root);
                
                System.out.println("[MarketDataService] Universe loaded. Tracking " + trackedSymbols.size() + " symbols.");
            }
        } catch (Exception e) {
            System.err.println("[MarketDataService] Failed to load market-universe.json: " + e.getMessage());
        }
    }

    private void extractSymbols(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (!trackedSymbols.contains(element.asText())) {
                    trackedSymbols.add(element.asText());
                }
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                extractSymbols(fields.next().getValue());
            }
        }
    }

    public List<String> getTrackedSymbols() {
        return trackedSymbols;
    }

    /**
     * Process an incoming tick update from Finnhub WS
     */
    public void processTick(LiveQuote tick) {
        String symbol = tick.getSymbol();
        
        // Either get existing to update, or create a new entry
        LiveQuote current = quotesCache.getOrDefault(symbol, LiveQuote.builder().symbol(symbol).build());
        
        current.setPrice(tick.getPrice());
        current.setTimestamp(tick.getTimestamp());
        current.setIsMarketOpen(marketHoursUtil.isMarketOpen(current.getExchange()));
        
        // Accumulate volume or set to new tick volume depending on stream rules
        if (tick.getVolume() != null) {
            current.setVolume(current.getVolume() != null ? current.getVolume() + tick.getVolume() : tick.getVolume());
        }
        
        // Calculate change metrics if we have prevClose
        if (current.getPrevClose() != null && current.getPrevClose() != 0.0) {
            double change = current.getPrice() - current.getPrevClose();
            current.setChange((double) Math.round(change * 100) / 100);
            
            double percent = (change / current.getPrevClose()) * 100;
            current.setChangePercent((double) Math.round(percent * 100) / 100);
        }

        quotesCache.put(symbol, current);
    }
    
    /**
     * Merge bulk quotes from Python Fallback Poller
     */
    public void processBulkQuotes(List<LiveQuote> batch) {
        for (LiveQuote quote : batch) {
            quote.setIsMarketOpen(marketHoursUtil.isMarketOpen(quote.getExchange()));
            quotesCache.put(quote.getSymbol(), quote);
        }
        System.out.println("[MarketDataService] Bulk cache updated from Python Poller.");
    }

    // --- Query Selectors ---

    public List<LiveQuote> getAllQuotes() {
        return new ArrayList<>(quotesCache.values());
    }

    public LiveQuote getLiveQuote(String symbol) {
        return quotesCache.get(symbol);
    }

    public List<LiveQuote> getQuotesByExchange(String exchange) {
        return quotesCache.values().stream()
                .filter(q -> exchange.equalsIgnoreCase(q.getExchange()))
                .collect(Collectors.toList());
    }

    public List<LiveQuote> getTopGainers(int limit) {
        return quotesCache.values().stream()
                .filter(q -> q.getChangePercent() != null && q.getChangePercent() >= 0)
                .sorted(Comparator.comparing(LiveQuote::getChangePercent).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<LiveQuote> getTopLosers(int limit) {
        return quotesCache.values().stream()
                .filter(q -> q.getChangePercent() != null && q.getChangePercent() < 0)
                .sorted(Comparator.comparing(LiveQuote::getChangePercent))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<LiveQuote> getTopByVolume(int limit) {
        return quotesCache.values().stream()
                .filter(q -> q.getVolume() != null)
                .sorted(Comparator.comparing(LiveQuote::getVolume).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
