USE fund;

RENAME TABLE cfg_fund TO fund_detail;

ALTER TABLE fund_detail
  RENAME INDEX uk_cfg_fund_code TO uk_fund_detail_code;
