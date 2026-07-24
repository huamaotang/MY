USE fund;

ALTER TABLE stock_daily_history
  ADD COLUMN `comment` VARCHAR(500) NULL COMMENT '备注' AFTER updated_at;
