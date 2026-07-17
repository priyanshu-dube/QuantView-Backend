package com.quantview.controller;

import com.quantview.model.LiveQuote;
import com.quantview.service.MarketDataService;
import com.quantview.util.MarketHoursUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller exposing real-time Server-Sent Events (SSE) 
 * for live market quotes, alongside standard REST lookups.
 */
@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final MarketHoursUtil marketHoursUtil;
    
    // Store active pure SSE connections
    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> symbolEmitters = new ConcurrentHashMap<>();


    public MarketDataController(MarketDataService marketDataService, MarketHoursUtil marketHoursUtil) {
        this.marketDataService = marketDataService;
        this.marketHoursUtil = marketHoursUtil;
        
        // Publish broadcast loop: Broadcasts the latest data exactly every 1000ms 
        ScheduledExecutorService broadcastExecutor = Executors.newSingleThreadScheduledExecutor();
        broadcastExecutor.scheduleAtFixedRate(this::broadcastMarketData, 2000, 1000, TimeUnit.MILLISECONDS);
    }

    /** 
     * SSE Endpoint: Broadcasts ALL quotes continuously (used for Market Overview & Ticker Tape) 
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAllQuotes() {
        // 30m timeout
        SseEmitter emitter = new SseEmitter(1800000L);
        
        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> globalEmitters.remove(emitter));
        emitter.onError(e -> globalEmitters.remove(emitter));

        globalEmitters.add(emitter);

        // Send initial connection burst
        try {
            emitter.send(SseEmitter.event().name("init").data("Connected to QuantView Market Stream"));
        } catch (IOException e) {
            globalEmitters.remove(emitter);
        }

        return emitter;
    }

    /** 
     * SSE Endpoint: Broadcasts purely for a single ticker (used for Dashboard view)
     */
    @GetMapping(value = "/stream/{symbol}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSymbol(@PathVariable String symbol) {
        String cleanSymbol = symbol.toUpperCase();
        SseEmitter emitter = new SseEmitter(1800000L);
        
        symbolEmitters.putIfAbsent(cleanSymbol, new CopyOnWriteArrayList<>());
        symbolEmitters.get(cleanSymbol).add(emitter);

        Runnable safeClean = () -> {
            List<SseEmitter> lst = symbolEmitters.get(cleanSymbol);
            if (lst != null) lst.remove(emitter);
        };
        
        emitter.onCompletion(safeClean);
        emitter.onTimeout(safeClean);
        emitter.onError(e -> safeClean.run());

        return emitter;
    }

    /** Background Broadcaster */
    private void broadcastMarketData() {
        if (globalEmitters.isEmpty() && symbolEmitters.isEmpty()) {
            return; // No connected clients, save CPU
        }

        List<LiveQuote> allQuotes = marketDataService.getAllQuotes();
        
        // Dispatch to all global streams
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name("quotes").data(allQuotes));
            } catch (IOException e) {
                globalEmitters.remove(emitter);
            }
        }

        // Dispatch specific symbol updates
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : symbolEmitters.entrySet()) {
            String symbol = entry.getKey();
            List<SseEmitter> emitters = entry.getValue();
            
            if (emitters.isEmpty()) continue;
            
            LiveQuote quote = marketDataService.getLiveQuote(symbol);
            if (quote != null) {
                for (SseEmitter emitter : emitters) {
                    try {
                        emitter.send(SseEmitter.event().name("quote_update").data(quote));
                    } catch (IOException e) {
                        emitters.remove(emitter);
                    }
                }
            }
        }
    }


    // ── REST Fallback / Overview Endpoints ──

    @GetMapping("/quotes")
    public ResponseEntity<List<LiveQuote>> getAllQuotes() {
        return ResponseEntity.ok(marketDataService.getAllQuotes());
    }

    @GetMapping("/quotes/{symbol}")
    public ResponseEntity<LiveQuote> getQuote(@PathVariable String symbol) {
        LiveQuote q = marketDataService.getLiveQuote(symbol.toUpperCase());
        return q != null ? ResponseEntity.ok(q) : ResponseEntity.notFound().build();
    }

    @GetMapping("/gainers")
    public ResponseEntity<List<LiveQuote>> getGainers(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(marketDataService.getTopGainers(limit));
    }

    @GetMapping("/losers")
    public ResponseEntity<List<LiveQuote>> getLosers(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(marketDataService.getTopLosers(limit));
    }
    
    @GetMapping("/volume")
    public ResponseEntity<List<LiveQuote>> getVolume(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(marketDataService.getTopByVolume(limit));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getMarketStatus() {
        return ResponseEntity.ok(Map.of(
            "NYSE", marketHoursUtil.isMarketOpen("NYSE"),
            "NSE", marketHoursUtil.isMarketOpen("NSE"),
            "CRYPTO", true
        ));
    }

    /** 
     * Internal ingestion endpoint used by Python market_poller fallback script 
     */
    @PostMapping("/internal/ingest")
    public ResponseEntity<String> ingestBulkQuotes(@RequestBody List<LiveQuote> quotes) {
        marketDataService.processBulkQuotes(quotes);
        return ResponseEntity.ok("Ingested " + quotes.size() + " quotes.");
    }
}
