USE fund;

CREATE TABLE IF NOT EXISTS fund_stock_holding (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  report_period VARCHAR(50) NULL COMMENT '报告期',
  report_date VARCHAR(8) NOT NULL COMMENT '报告期截止日期',
  rank_no INT NULL COMMENT '序号',
  stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
  stock_name VARCHAR(100) NULL COMMENT '股票名称',
  latest_price DECIMAL(18,4) NULL COMMENT '最新价',
  change_rate DECIMAL(10,4) NULL COMMENT '涨跌幅',
  related_info_url VARCHAR(255) NULL COMMENT '相关资讯',
  net_value_ratio DECIMAL(10,4) NULL COMMENT '占净值比例',
  holding_shares_10k DECIMAL(20,4) NULL COMMENT '持股数（万股）',
  holding_market_value_10k DECIMAL(20,4) NULL COMMENT '持仓市值（万元）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_stock_holding (fund_code, report_date, stock_code),
  KEY idx_fund_stock_report_date (report_date),
  KEY idx_fund_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓表';
