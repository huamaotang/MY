USE fund;

ALTER TABLE fund_holding_import_item
  ADD COLUMN holding_cost DECIMAL(20,4) NULL COMMENT '持仓成本' AFTER holding_return_rate;

ALTER TABLE user_fund_holding
  ADD COLUMN holding_cost DECIMAL(20,4) NULL COMMENT '持仓成本' AFTER holding_return_rate;

UPDATE fund_holding_import_item
SET holding_cost = ROUND(holding_amount - holding_profit, 4)
WHERE holding_cost IS NULL
  AND holding_amount IS NOT NULL
  AND holding_profit IS NOT NULL
  AND holding_amount - holding_profit >= 0;

UPDATE user_fund_holding
SET holding_cost = ROUND(holding_amount - holding_profit, 4)
WHERE holding_cost IS NULL
  AND holding_amount IS NOT NULL
  AND holding_profit IS NOT NULL
  AND holding_amount - holding_profit >= 0;
