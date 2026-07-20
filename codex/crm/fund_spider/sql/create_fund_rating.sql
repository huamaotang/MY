CREATE TABLE IF NOT EXISTS fund_rating (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
  rating_date VARCHAR(8) NOT NULL COMMENT '评级日期',
  zhaoshang_rating TINYINT UNSIGNED NULL COMMENT '招商评级',
  shanghai_rating_3y TINYINT UNSIGNED NULL COMMENT '上海证券三年期评级',
  shanghai_rating_5y TINYINT UNSIGNED NULL COMMENT '上海证券五年期评级',
  jian_rating TINYINT UNSIGNED NULL COMMENT '济安金信评级',
  morning_star_rating TINYINT UNSIGNED NULL COMMENT '晨星评级',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fund_rating_code_date (fund_code, rating_date),
  KEY idx_fund_rating_date (rating_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评级表';
