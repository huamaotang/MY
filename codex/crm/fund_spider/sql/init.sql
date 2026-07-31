CREATE DATABASE IF NOT EXISTS fund DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE fund;

CREATE TABLE IF NOT EXISTS fund_detail (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  fund_name VARCHAR(255) NOT NULL COMMENT '基金名称',
  inception_date DATE NULL COMMENT '成立日期',
  fund_manager VARCHAR(255) NULL COMMENT '基金经理',
  fund_type VARCHAR(100) NULL COMMENT '类型',
  management_company VARCHAR(255) NULL COMMENT '管理人',
  net_asset_scale VARCHAR(100) NULL COMMENT '净资产规模',
  scale_date DATE NULL COMMENT '规模截止至日',
  profile_updated_at DATETIME NULL COMMENT '基础资料更新时间',
  can_buy TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可购买',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_detail_code (fund_code)
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

CREATE TABLE IF NOT EXISTS fund_performance_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  nav_date VARCHAR(8) NOT NULL COMMENT '净值日期',
  fund_name_pinyin VARCHAR(255) NULL COMMENT '基金简称拼音',
  inception_date DATE NULL COMMENT '成立日期',
  weekly_return_rate DECIMAL(14,4) NULL COMMENT '近一周收益率',
  monthly_return_rate DECIMAL(14,4) NULL COMMENT '近一月收益率',
  three_month_return_rate DECIMAL(14,4) NULL COMMENT '近三月收益率',
  six_month_return_rate DECIMAL(14,4) NULL COMMENT '近六月收益率',
  one_year_return_rate DECIMAL(14,4) NULL COMMENT '近一年收益率',
  two_year_return_rate DECIMAL(14,4) NULL COMMENT '近两年收益率',
  three_year_return_rate DECIMAL(14,4) NULL COMMENT '近三年收益率',
  year_to_date_return_rate DECIMAL(14,4) NULL COMMENT '今年以来收益率',
  since_inception_return_rate DECIMAL(14,4) NULL COMMENT '成立以来收益率',
  custom_start_date DATE NOT NULL COMMENT '自定义区间开始日期',
  custom_end_date DATE NOT NULL COMMENT '自定义区间结束日期',
  custom_return_rate DECIMAL(14,4) NULL COMMENT '自定义区间收益率',
  sale_status VARCHAR(10) NULL COMMENT '东方财富销售状态码',
  original_fee_rate DECIMAL(10,4) NULL COMMENT '原手续费率',
  discounted_fee_rate DECIMAL(10,4) NULL COMMENT '折后手续费率',
  discount_factor DECIMAL(10,4) NULL COMMENT '折扣',
  cash_management_fee_rate DECIMAL(10,4) NULL COMMENT '活期宝手续费率',
  source_row TEXT NOT NULL COMMENT '接口原始行',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_performance_code_date (fund_code, nav_date),
  KEY idx_fund_performance_nav_date (nav_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金业绩表现历史表';

CREATE TABLE IF NOT EXISTS fund_stock_holding (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  report_period VARCHAR(50) NULL COMMENT '报告期',
  report_date VARCHAR(8) NOT NULL COMMENT '报告期截止日期',
  cutoff_date VARCHAR(8) NOT NULL COMMENT '页面截止至日期',
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
  KEY idx_fund_stock_cutoff_date (fund_code, cutoff_date),
  KEY idx_fund_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓表';

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

CREATE TABLE IF NOT EXISTS fund_refresh_state (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  data_type VARCHAR(20) NOT NULL COMMENT '数据类型',
  last_success_at DATETIME NOT NULL COMMENT '最近成功刷新时间',
  last_row_count INT NOT NULL DEFAULT 0 COMMENT '最近刷新行数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_refresh_state_code_type (fund_code, data_type),
  KEY idx_fund_refresh_state_type_time (data_type, last_success_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金数据刷新状态表';

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

CREATE TABLE IF NOT EXISTS yangjibao_news (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  news_id VARCHAR(32) NOT NULL COMMENT '养基宝资讯ID',
  title VARCHAR(500) NULL COMMENT '标题',
  content TEXT NOT NULL COMMENT '正文',
  display_time DATETIME NOT NULL COMMENT '展示时间',
  images_json JSON NULL COMMENT '图片列表',
  score INT NULL COMMENT '重要级别',
  news_type INT NULL COMMENT '资讯类型',
  source_json JSON NOT NULL COMMENT '接口原始数据',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_yangjibao_news_id (news_id),
  KEY idx_yangjibao_news_display_time (display_time),
  KEY idx_yangjibao_news_score (score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='养基宝资讯表';

CREATE TABLE IF NOT EXISTS sina_finance_news (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  news_id VARCHAR(32) NOT NULL,
  category_tag INT NOT NULL DEFAULT 0 COMMENT '频道标签：0全部，10 A股',
  category_name VARCHAR(50) NOT NULL DEFAULT '全部' COMMENT '频道名称',
  content TEXT NOT NULL,
  create_time DATETIME NOT NULL,
  source_update_time DATETIME NOT NULL,
  doc_url VARCHAR(1000) NULL,
  tags_json JSON NULL,
  images_json JSON NULL,
  source_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sina_finance_news_id (news_id),
  KEY idx_sina_finance_news_category_time (category_tag, create_time),
  KEY idx_sina_finance_news_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新浪财经7x24资讯表';

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
  `comment` VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (id), UNIQUE KEY uk_stock_daily (stock_code, trade_date),
  KEY idx_stock_daily_date (trade_date), KEY idx_stock_daily_date_change (trade_date, change_rate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票每日行情表';
