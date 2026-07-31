SET @import_type_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'fund_holding_import'
    AND COLUMN_NAME = 'import_type'
);

SET @add_import_type_sql = IF(
  @import_type_exists = 0,
  'ALTER TABLE fund_holding_import ADD COLUMN import_type VARCHAR(32) NOT NULL DEFAULT ''holding'' COMMENT ''导入类型：holding/trade'' AFTER source_label',
  'SELECT ''fund_holding_import.import_type already exists'''
);

PREPARE add_import_type_statement FROM @add_import_type_sql;
EXECUTE add_import_type_statement;
DEALLOCATE PREPARE add_import_type_statement;

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
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_trade_import_applied_key (applied_key),
  KEY idx_fund_trade_import_batch_group (import_id, group_key),
  KEY idx_fund_trade_import_fingerprint (fingerprint),
  KEY idx_fund_trade_import_fund_code (fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金交易截图导入明细表';
