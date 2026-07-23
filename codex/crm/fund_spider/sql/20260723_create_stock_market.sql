CREATE TABLE IF NOT EXISTS stock_detail (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stock_code VARCHAR(20) NOT NULL, stock_name VARCHAR(100) NOT NULL,
  market_code INT NOT NULL, exchange_name VARCHAR(20) NOT NULL, listing_date DATE NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_stock_detail_code (stock_code),
  KEY idx_stock_detail_name (stock_name), KEY idx_stock_detail_market (market_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票基础信息表';

CREATE TABLE IF NOT EXISTS stock_daily_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stock_code VARCHAR(20) NOT NULL, trade_date DATE NOT NULL, quote_time DATETIME NULL,
  latest_price DECIMAL(20,4) NULL, change_rate DECIMAL(12,4) NULL, change_amount DECIMAL(20,4) NULL,
  volume BIGINT NULL, amount DECIMAL(24,4) NULL, amplitude DECIMAL(12,4) NULL,
  turnover_rate DECIMAL(12,4) NULL, pe_dynamic DECIMAL(20,4) NULL, volume_ratio DECIMAL(12,4) NULL,
  five_min_change_rate DECIMAL(12,4) NULL, high_price DECIMAL(20,4) NULL, low_price DECIMAL(20,4) NULL,
  open_price DECIMAL(20,4) NULL, previous_close DECIMAL(20,4) NULL,
  total_market_cap DECIMAL(24,4) NULL, float_market_cap DECIMAL(24,4) NULL,
  speed_rate DECIMAL(12,4) NULL, pb_ratio DECIMAL(20,4) NULL,
  change_rate_60d DECIMAL(12,4) NULL, change_rate_ytd DECIMAL(12,4) NULL,
  main_net_inflow DECIMAL(24,4) NULL, pe_ttm DECIMAL(20,4) NULL, raw_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_stock_daily (stock_code, trade_date),
  KEY idx_stock_daily_date (trade_date), KEY idx_stock_daily_date_change (trade_date, change_rate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票每日行情表';
