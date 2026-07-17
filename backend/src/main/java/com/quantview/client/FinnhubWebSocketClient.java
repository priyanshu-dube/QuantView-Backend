package com.quantview.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantview.model.LiveQuote;
import com.quantview.service.MarketDataService;
import com.quantview.util.MarketHoursUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Connects to Finnhub's WebSocket stream to receive real-time tick data.
 */
@Component
public class FinnhubWebSocketClient {

    private final String finnhubUrl;
    private final String apiKey;
    private final MarketDataService marketDataService;
    private final MarketHoursUtil marketHoursUtil;
    private final ObjectMapper objectMapper;
    
    private WebSocketClient webSocketClient;
    private ScheduledExecutorService reconnectScheduler;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    public FinnhubWebSocketClient(
            @Value("${finnhub.ws.url:wss://ws.finnhub.io}") String finnhubUrl,
            @Value("${finnhub.api.key}") String apiKey,
            MarketDataService marketDataService,
            MarketHoursUtil marketHoursUtil) {
        
        this.finnhubUrl = finnhubUrl;
        this.apiKey = apiKey;
        this.marketDataService = marketDataService;
        this.marketHoursUtil = marketHoursUtil;
        this.objectMapper = new ObjectMapper();
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isEmpty() || "sandbox_key_here".equals(apiKey)) {
            System.out.println("[FinnhubClient] Finnhub API Key is missing or default. WebSocket will likely fail authentication.");
        }
        connectToFinnhub();
    }

    private void connectToFinnhub() {
        try {
            URI uri = new URI(finnhubUrl + "?token=" + apiKey);
            
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("[FinnhubClient] Connected successfully to Finnhub API.");
                    reconnectAttempts = 0;
                    subscribeToUniverse();
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("[FinnhubClient] Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code + " Reason: " + reason);
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("[FinnhubClient] WebSocket error: " + ex.getMessage());
                }
            };
            
            webSocketClient.connect();
        } catch (Exception e) {
            System.err.println("[FinnhubClient] Connection failure: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void handleIncomingMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root.has("type") && "trade".equals(root.get("type").asText())) {
                JsonNode dataArray = root.get("data");
                if (dataArray != null && dataArray.isArray()) {
                    for (JsonNode trade : dataArray) {
                        String symbol = trade.get("s").asText();
                        double price = trade.get("p").asDouble();
                        long volume = trade.get("v").asLong();
                        long timestamp = trade.get("t").asLong();

                        // Dispatch valid update
                        LiveQuote update = LiveQuote.builder()
                                .symbol(symbol)
                                .price(price)
                                .volume(volume)
                                .timestamp(timestamp)
                                .build();
                                
                        marketDataService.processTick(update);
                    }
                }
            } else if (root.has("type") && "ping".equals(root.get("type").asText())) {
                // Ignore pings
            }
        } catch (Exception e) {
            System.err.println("[FinnhubClient] Error processing message: " + e.getMessage() + " | message: " + message);
        }
    }

    private void subscribeToUniverse() {
        // We defer to the MarketDataService which loaded the universe.json
        List<String> symbols = marketDataService.getTrackedSymbols();
        
        for (String symbol : symbols) {
            String msg = "{\"type\":\"subscribe\",\"symbol\":\"" + symbol + "\"}";
            try {
                if (webSocketClient.isOpen()) {
                    webSocketClient.send(msg);
                }
            } catch (Exception e) {
                System.out.println("[FinnhubClient] Failed to subscribe to " + symbol);
            }
        }
        System.out.println("[FinnhubClient] Sent subscriptions for " + symbols.size() + " tickers.");
    }

    private void scheduleReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            int delaySeconds = Math.min(5 * reconnectAttempts, 30); // Exponential-ish backoff
            System.out.println("[FinnhubClient] Attempting to reconnect in " + delaySeconds + " seconds... (Attempt " + reconnectAttempts + ")");
            
            reconnectScheduler.schedule(this::connectToFinnhub, delaySeconds, TimeUnit.SECONDS);
        } else {
            System.err.println("[FinnhubClient] Max reconnect attempts reached. Relinquishing to Python Poller Backup exclusively.");
        }
    }

    @PreDestroy
    public void cleanup() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
        }
    }
}
