USE fund;

CREATE TABLE IF NOT EXISTS user_fund_favorite (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_username VARCHAR(64) NOT NULL COMMENT '归属用户',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_fund_favorite_owner_code (owner_username, fund_code),
  KEY idx_user_fund_favorite_owner_time (owner_username, created_at),
  KEY idx_user_fund_favorite_fund_code (fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基金自选表';
