package com.quantview;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import org.springframework.cache.annotation.EnableCaching;

/**
 * Main entry point for the QuantView Spring Boot backend.
 * Checks Python ML engine health on startup.
 */
@SpringBootApplication
@EnableCaching
public class QuantViewApplication {

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  QuantView Backend — Orchestration Layer");
        System.out.println("  Running on http://localhost:8080");
        System.out.println("=".repeat(60) + "\n");

        SpringApplication.run(QuantViewApplication.class, args);
    }

    /**
     * On startup, probe the Python ML engine's /health endpoint.
     * Logs a warning if unreachable — the app will still start,
     * but stock predictions will return 503 until Python is online.
     */
    @Bean
    public CommandLineRunner checkPythonHealth(
            @Value("${python.service.url:http://localhost:5000}") String pythonUrl) {

        return args -> {
            System.out.println("[Startup] Checking Python ML engine at " + pythonUrl + "/health ...");

            try {
                RestTemplate restTemplate = new RestTemplate();
                String response = restTemplate.getForObject(pythonUrl + "/health", String.class);
                System.out.println("[Startup] Python ML engine is ONLINE: " + response);
            } catch (Exception e) {
                System.out.println("=".repeat(60));
                System.out.println("  WARNING: Python ML engine is UNREACHABLE");
                System.out.println("  URL: " + pythonUrl);
                System.out.println("  Error: " + e.getMessage());
                System.out.println("  Stock predictions will return 503 until Python is started.");
                System.out.println("=".repeat(60));
            }
        };
    }
}
