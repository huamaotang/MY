USE fund;

ALTER TABLE fund_detail
  ADD COLUMN can_buy TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可购买' AFTER profile_updated_at;

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
