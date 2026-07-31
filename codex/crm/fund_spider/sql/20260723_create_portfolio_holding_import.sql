CREATE DATABASE IF NOT EXISTS fund
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE fund;

CREATE TABLE IF NOT EXISTS fund_holding_import (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_username VARCHAR(64) NOT NULL COMMENT '归属用户',
  source_label VARCHAR(32) NOT NULL COMMENT '来源标识',
  import_type VARCHAR(32) NOT NULL DEFAULT 'holding' COMMENT '导入类型：holding/trade',
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

CREATE TABLE IF NOT EXISTS fund_holding_trade_import_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  import_id BIGINT UNSIGNED NOT NULL COMMENT '导入批次ID',
  row_no INT NOT NULL COMMENT 'OCR行号',
  group_key VARCHAR(64) NOT NULL COMMENT '基金汇总分组键',
  fund_code VARCHAR(20) NULL COMMENT '匹配后的基金代码',
  fund_name VARCHAR(255) NULL COMMENT 'OCR基金名称',
  operation_type VARCHAR(16) NULL COMMENT '交易方向：BUY/SELL',
  transaction_amount DECIMAL(20,4) NULL COMMENT '交易金额',
  transaction_at DATETIME NULL COMMENT '交易时间',
  transaction_status VARCHAR(32) NULL COMMENT '识别状态：SUCCESS/UNKNOWN/FAILED',
  screenshot_date DATE NULL COMMENT '截图日期',
  confidence DECIMAL(10,4) NULL COMMENT '识别置信度',
  fingerprint VARCHAR(64) NOT NULL COMMENT '交易业务指纹',
  applied_key VARCHAR(64) NULL COMMENT '已应用幂等键',
  status VARCHAR(32) NOT NULL COMMENT 'APPLICABLE/APPLIED/SKIPPED_*',
  skip_reason VARCHAR(255) NULL COMMENT '跳过原因',
  before_holding_amount DECIMAL(20,4) NULL COMMENT '应用前持有金额',
  after_holding_amount DECIMAL(20,4) NULL COMMENT '应用后持有金额',
  raw_text_json JSON NULL COMMENT '原始OCR文本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT='更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_trade_import_applied_key (applied_key),
  KEY idx_fund_trade_import_batch_group (import_id, group_key),
  KEY idx_fund_trade_import_fingerprint (fingerprint),
  KEY idx_fund_trade_import_fund_code (fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金交易截图导入明细表';

CREATE TABLE IF NOT EXISTS user_fund_holding (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_username VARCHAR(64) NOT NULL COMMENT '归属用户',
  source_label VARCHAR(32) NOT NULL DEFAULT 'alipay' COMMENT '账户来源',
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
  UNIQUE KEY uk_user_fund_holding_owner_source_code (owner_username, source_label, fund_code),
  KEY idx_user_fund_holding_owner_source (owner_username, source_label),
  KEY idx_user_fund_holding_owner_time (owner_username, latest_import_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基金持仓表';
