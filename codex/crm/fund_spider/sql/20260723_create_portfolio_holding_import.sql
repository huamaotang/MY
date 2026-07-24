CREATE DATABASE IF NOT EXISTS fund
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE fund;

CREATE TABLE IF NOT EXISTS fund_holding_import (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_username VARCHAR(64) NOT NULL COMMENT '归属用户',
  source_label VARCHAR(32) NOT NULL COMMENT '来源标识',
  status VARCHAR(32) NOT NULL COMMENT '状态',
  screenshot_date DATE NULL COMMENT '截图日期',
  image_count INT NOT NULL DEFAULT 0 COMMENT '图片数量',
  image_hashes_json JSON NULL COMMENT '图片哈希',
  raw_ocr_json JSON NULL COMMENT '原始OCR结果',
  warnings_json JSON NULL COMMENT '识别告警',
  parser_version VARCHAR(64) NULL COMMENT '解析器版本',
  confirmed_at DATETIME NULL COMMENT '确认时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_fund_holding_import_owner_time (owner_username, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓导入批次表';

CREATE TABLE IF NOT EXISTS fund_holding_import_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  import_id BIGINT UNSIGNED NOT NULL COMMENT '导入批次ID',
  row_no INT NOT NULL COMMENT '行号',
  fund_code VARCHAR(20) NULL COMMENT '基金代码',
  fund_name VARCHAR(255) NULL COMMENT '基金名称',
  holding_amount DECIMAL(20,4) NULL COMMENT '持有金额',
  holding_profit DECIMAL(20,4) NULL COMMENT '持有收益',
  holding_return_rate DECIMAL(20,4) NULL COMMENT '持有收益率',
  holding_cost DECIMAL(20,4) NULL COMMENT '持仓成本',
  yesterday_profit DECIMAL(20,4) NULL COMMENT '昨日收益',
  today_profit DECIMAL(20,4) NULL COMMENT '今日收益',
  holding_shares DECIMAL(20,4) NULL COMMENT '持有份额',
  cost_nav DECIMAL(20,6) NULL COMMENT '成本净值',
  screenshot_date DATE NULL COMMENT '截图日期',
  confidence DECIMAL(10,4) NULL COMMENT '识别置信度',
  candidate_json JSON NULL COMMENT '候选基金',
  raw_text_json JSON NULL COMMENT '原始文本',
  status VARCHAR(32) NOT NULL COMMENT '状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_fund_holding_import_item_import (import_id),
  KEY idx_fund_holding_import_item_fund_code (fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓导入明细表';

CREATE TABLE IF NOT EXISTS user_fund_holding (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_username VARCHAR(64) NOT NULL COMMENT '归属用户',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  fund_name VARCHAR(255) NOT NULL COMMENT '基金名称',
  holding_amount DECIMAL(20,4) NULL COMMENT '持有金额',
  holding_profit DECIMAL(20,4) NULL COMMENT '持有收益',
  holding_return_rate DECIMAL(20,4) NULL COMMENT '持有收益率',
  holding_cost DECIMAL(20,4) NULL COMMENT '持仓成本',
  yesterday_profit DECIMAL(20,4) NULL COMMENT '昨日收益',
  today_profit DECIMAL(20,4) NULL COMMENT '今日收益',
  holding_shares DECIMAL(20,4) NULL COMMENT '持有份额',
  cost_nav DECIMAL(20,6) NULL COMMENT '成本净值',
  screenshot_date DATE NULL COMMENT '截图日期',
  latest_import_id BIGINT UNSIGNED NULL COMMENT '最近导入批次ID',
  latest_import_at DATETIME NULL COMMENT '最近导入时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_fund_holding_owner_code (owner_username, fund_code),
  KEY idx_user_fund_holding_owner_time (owner_username, latest_import_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基金持仓表';
