ALTER TABLE user_fund_holding
  ADD COLUMN source_label VARCHAR(32) NOT NULL DEFAULT 'alipay' COMMENT '账户来源：alipay/tencent'
  AFTER owner_username;

UPDATE user_fund_holding h
LEFT JOIN fund_holding_import b ON b.id = h.latest_import_id
SET h.source_label = CASE
  WHEN b.source_label IN ('alipay', 'tencent') THEN b.source_label
  ELSE 'alipay'
END;

ALTER TABLE user_fund_holding
  DROP INDEX uk_user_fund_holding_owner_code,
  ADD UNIQUE KEY uk_user_fund_holding_owner_source_code (owner_username, source_label, fund_code),
  ADD KEY idx_user_fund_holding_owner_source (owner_username, source_label);
