USE fund;

CREATE TABLE IF NOT EXISTS fund_scale_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  scale_date VARCHAR(8) NOT NULL COMMENT '规模日期',
  net_asset_scale_yi DECIMAL(20,4) NULL COMMENT '净资产规模（亿元）',
  source_text VARCHAR(100) NULL COMMENT '来源原文',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_scale_code_date (fund_code, scale_date),
  KEY idx_fund_scale_date (scale_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金历史规模';

CREATE TABLE IF NOT EXISTS fund_score_profile (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  profile_name VARCHAR(100) NOT NULL COMMENT '方案名称',
  version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_BACKTEST/CANDIDATE/ACTIVE/ARCHIVED',
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SEED/BACKTEST',
  target_months INT NOT NULL DEFAULT 12 COMMENT '预测月数',
  weights_json JSON NOT NULL COMMENT '叶子权重',
  calibration_json JSON NULL COMMENT '概率校准曲线',
  validation_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/PASSED/FAILED',
  is_active TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用',
  created_by VARCHAR(64) NULL,
  approved_by VARCHAR(64) NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_score_profile_name_version (profile_name, version_no),
  KEY idx_fund_score_profile_active (is_active, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评分权重方案';

CREATE TABLE IF NOT EXISTS fund_score_factor_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  as_of_date VARCHAR(8) NOT NULL COMMENT '评分日期',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  fund_type VARCHAR(100) NULL COMMENT '基金细分类型',
  comparison_group VARCHAR(100) NULL COMMENT '实际比较组',
  factors_json JSON NOT NULL COMMENT '原始因子',
  normalized_json JSON NULL COMMENT '同类标准化因子',
  data_coverage DECIMAL(8,6) NOT NULL DEFAULT 0 COMMENT '权重覆盖率',
  forward_return DECIMAL(18,8) NULL COMMENT '未来12月收益率',
  profitable TINYINT(1) NULL COMMENT '未来12月是否盈利',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_score_factor_date_code (as_of_date, fund_code),
  KEY idx_fund_score_factor_code_date (fund_code, as_of_date),
  KEY idx_fund_score_factor_group_date (comparison_group, as_of_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评分因子快照';

CREATE TABLE IF NOT EXISTS fund_score_result (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  profile_id BIGINT UNSIGNED NOT NULL COMMENT '评分方案',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  as_of_date VARCHAR(8) NOT NULL COMMENT '评分日期',
  total_score DECIMAL(8,4) NULL COMMENT '0-100总分',
  profit_probability DECIMAL(8,6) NULL COMMENT '0-1未来一年盈利概率',
  confidence VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT 'HIGH/MEDIUM/LOW/INSUFFICIENT',
  data_coverage DECIMAL(8,6) NOT NULL DEFAULT 0 COMMENT '数据覆盖率',
  comparison_group VARCHAR(100) NULL COMMENT '比较组',
  category_rank INT NULL COMMENT '组内排名',
  category_count INT NULL COMMENT '组内数量',
  components_json JSON NULL COMMENT '分项贡献',
  methodology_version VARCHAR(32) NOT NULL DEFAULT 'fund-score-v1',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_score_result_profile_code_date (profile_id, fund_code, as_of_date),
  KEY idx_fund_score_result_active_list (profile_id, as_of_date, total_score),
  KEY idx_fund_score_result_code_date (fund_code, as_of_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评分结果';

CREATE TABLE IF NOT EXISTS fund_score_backtest (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  profile_id BIGINT UNSIGNED NOT NULL COMMENT '评分方案',
  train_start_date VARCHAR(8) NULL,
  train_end_date VARCHAR(8) NULL,
  test_start_date VARCHAR(8) NULL,
  test_end_date VARCHAR(8) NULL,
  sample_count INT NOT NULL DEFAULT 0,
  fold_count INT NOT NULL DEFAULT 0,
  auc DECIMAL(10,6) NULL,
  brier_score DECIMAL(10,6) NULL,
  baseline_brier_score DECIMAL(10,6) NULL,
  top20_win_rate DECIMAL(10,6) NULL,
  baseline_win_rate DECIMAL(10,6) NULL,
  win_rate_lift DECIMAL(10,6) NULL,
  passed TINYINT(1) NOT NULL DEFAULT 0,
  limitations_json JSON NULL,
  metrics_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_fund_score_backtest_profile_time (profile_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评分回测';

CREATE TABLE IF NOT EXISTS fund_score_job (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  job_type VARCHAR(32) NOT NULL COMMENT 'CURRENT_SCORE/BACKTEST/RECOMMEND',
  profile_id BIGINT UNSIGNED NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  requested_by VARCHAR(64) NULL,
  message VARCHAR(1000) NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_fund_score_job_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评分任务';

INSERT INTO fund_score_profile (
  profile_name, version_no, status, source_type, target_months, weights_json,
  validation_status, is_active, created_by
)
SELECT
  '保守初始权重', 1, 'ACTIVE', 'SEED', 12,
  JSON_OBJECT(
    'return_1m', 1, 'return_3m', 3, 'return_6m', 5,
    'return_1y', 7, 'return_2y', 5, 'return_3y', 4,
    'volatility_1y', 5, 'volatility_3y', 10,
    'sharpe_1y', 10, 'sharpe_3y', 15,
    'drawdown_1y', 8, 'drawdown_3y', 12,
    'rating_zhaoshang', 2, 'rating_shanghai_3y', 2,
    'rating_shanghai_5y', 1, 'rating_jian', 2,
    'rating_morningstar', 3, 'scale', 5
  ),
  'UNVERIFIED', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM fund_score_profile);
