USE fund;

ALTER TABLE fund_holding_import_item
  ADD COLUMN cost_nav DECIMAL(20,6) NULL COMMENT '成本净值' AFTER holding_cost;

ALTER TABLE user_fund_holding
  ADD COLUMN cost_nav DECIMAL(20,6) NULL COMMENT '成本净值' AFTER holding_cost;

UPDATE fund_holding_import_item
SET cost_nav = ROUND(holding_cost / holding_shares, 6)
WHERE cost_nav IS NULL
  AND holding_cost IS NOT NULL
  AND holding_shares IS NOT NULL
  AND holding_shares > 0;

UPDATE user_fund_holding
SET cost_nav = ROUND(holding_cost / holding_shares, 6)
WHERE cost_nav IS NULL
  AND holding_cost IS NOT NULL
  AND holding_shares IS NOT NULL
  AND holding_shares > 0;
