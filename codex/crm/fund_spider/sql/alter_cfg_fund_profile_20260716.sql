USE fund;

ALTER TABLE cfg_fund
  ADD COLUMN inception_date DATE NULL COMMENT '成立日期',
  ADD COLUMN fund_manager VARCHAR(255) NULL COMMENT '基金经理',
  ADD COLUMN fund_type VARCHAR(100) NULL COMMENT '类型',
  ADD COLUMN management_company VARCHAR(255) NULL COMMENT '管理人',
  ADD COLUMN net_asset_scale VARCHAR(100) NULL COMMENT '净资产规模',
  ADD COLUMN scale_date DATE NULL COMMENT '规模截止至日';
