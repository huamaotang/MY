CREATE DATABASE IF NOT EXISTS fund DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE fund;

CREATE TABLE IF NOT EXISTS cfg_fund (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  fund_name VARCHAR(255) NOT NULL COMMENT '基金名称',
  inception_date DATE NULL COMMENT '成立日期',
  fund_manager VARCHAR(255) NULL COMMENT '基金经理',
  fund_type VARCHAR(100) NULL COMMENT '类型',
  management_company VARCHAR(255) NULL COMMENT '管理人',
  net_asset_scale VARCHAR(100) NULL COMMENT '净资产规模',
  scale_date DATE NULL COMMENT '规模截止至日',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cfg_fund_code (fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金配置表';

CREATE TABLE IF NOT EXISTS fund_nav_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  nav_date VARCHAR(8) NOT NULL COMMENT '净值日期',
  unit_nav DECIMAL(18,6) NULL COMMENT '单位净值',
  accumulated_nav DECIMAL(18,6) NULL COMMENT '累计净值',
  daily_growth_rate DECIMAL(10,4) NULL COMMENT '日增长率',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_nav_code_date (fund_code, nav_date),
  KEY idx_fund_nav_date (nav_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金历史净值表';

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

CREATE TABLE IF NOT EXISTS fund_feature_data (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  period_label VARCHAR(20) NOT NULL COMMENT '统计周期',
  cutoff_date VARCHAR(8) NOT NULL COMMENT '截止日期',
  standard_deviation DECIMAL(10,4) NULL COMMENT '标准差',
  sharpe_ratio DECIMAL(10,4) NULL COMMENT '夏普比率',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_feature_period (fund_code, cutoff_date, period_label),
  KEY idx_fund_feature_cutoff_date (cutoff_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金特色数据表';

CREATE TABLE IF NOT EXISTS fund_rating (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  rating_date VARCHAR(8) NOT NULL COMMENT '评级日期',
  zhaoshang_rating TINYINT UNSIGNED NULL COMMENT '招商评级',
  shanghai_rating_3y TINYINT UNSIGNED NULL COMMENT '上海证券三年期评级',
  shanghai_rating_5y TINYINT UNSIGNED NULL COMMENT '上海证券五年期评级',
  jian_rating TINYINT UNSIGNED NULL COMMENT '济安金信评级',
  morning_star_rating TINYINT UNSIGNED NULL COMMENT '晨星评级',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_rating_code_date (fund_code, rating_date),
  KEY idx_fund_rating_date (rating_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评级表';

CREATE TABLE IF NOT EXISTS fund_crawl_cursor (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  job_name VARCHAR(50) NOT NULL COMMENT '任务名称',
  cursor_date VARCHAR(8) NOT NULL COMMENT '游标日期',
  last_fund_code VARCHAR(20) NULL COMMENT '最后成功基金代码',
  completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否完成',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_crawl_cursor_job_date (job_name, cursor_date),
  KEY idx_fund_crawl_cursor_date (cursor_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金抓取游标表';
