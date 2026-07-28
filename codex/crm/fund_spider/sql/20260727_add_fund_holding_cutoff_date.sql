USE fund;

ALTER TABLE fund_stock_holding
  ADD COLUMN cutoff_date VARCHAR(8) NULL COMMENT '页面截止至日期' AFTER report_date;

UPDATE fund_stock_holding
SET cutoff_date = report_date
WHERE cutoff_date IS NULL OR cutoff_date = '';

ALTER TABLE fund_stock_holding
  MODIFY COLUMN cutoff_date VARCHAR(8) NOT NULL COMMENT '页面截止至日期',
  ADD KEY idx_fund_stock_cutoff_date (fund_code, cutoff_date);
