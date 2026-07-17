USE fund;

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
