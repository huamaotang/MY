USE fund;

ALTER TABLE fund_nav_history
  MODIFY COLUMN nav_date VARCHAR(10) NOT NULL COMMENT '净值日期';

UPDATE fund_nav_history
SET nav_date = REPLACE(nav_date, '-', '')
WHERE nav_date LIKE '%-%';

ALTER TABLE fund_nav_history
  MODIFY COLUMN nav_date VARCHAR(8) NOT NULL COMMENT '净值日期';

ALTER TABLE fund_nav_history
  DROP COLUMN subscription_status,
  DROP COLUMN redemption_status,
  DROP COLUMN dividend,
  DROP COLUMN nav_type;
