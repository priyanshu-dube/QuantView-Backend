-- =============================================================
-- QuantView Database Schema
-- =============================================================
-- Run this against MySQL to set up the required tables.
-- Spring Boot's ddl-auto=update will also create these tables
-- automatically, but this file serves as the canonical schema.
-- =============================================================

CREATE DATABASE IF NOT EXISTS quantview
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE quantview;

-- ── Watchlist ──────────────────────────────────────────────────
-- Stores user-saved stock tickers. Each user can save a ticker
-- only once (enforced by UNIQUE constraint).

CREATE TABLE IF NOT EXISTS watchlist (
    id         INT          NOT NULL AUTO_INCREMENT,
    user_id    VARCHAR(255) NOT NULL COMMENT 'Clerk user ID',
    ticker     VARCHAR(20)  NOT NULL,
    added_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_user_ticker (user_id, ticker),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ── Prediction Cache ──────────────────────────────────────────
-- Caches the full JSON response from the Python ML engine.
-- Each ticker has at most one cache entry (UNIQUE on ticker).
-- Spring Boot checks cached_at to enforce 15-minute TTL.

CREATE TABLE IF NOT EXISTS prediction_cache (
    id              INT          NOT NULL AUTO_INCREMENT,
    ticker          VARCHAR(20)  NOT NULL,
    prediction_json LONGTEXT     NOT NULL COMMENT 'Full JSON blob from Python ML engine',
    cached_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_ticker (ticker),
    INDEX idx_ticker_cached (ticker, cached_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
