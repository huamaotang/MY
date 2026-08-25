USE fund;

-- 评分因子升级：新增"当日预估跌幅"（decline_today）因子，跌幅越大分值越高。
-- 在保留其他因子的前提下兼容补充：默认方案权重按最新 DEFAULT_WEIGHTS 整体重排并保持总和 100；
-- 其他已有方案用 JSON_INSERT 补入 decline_today（权重 0），不覆盖用户自定义权重。
-- 需与 scoring.py DEFAULT_WEIGHTS、db.py 种子、Java FACTOR_KEYS、前端 SCORE_FACTORS 保持一致。

UPDATE fund_score_profile
SET weights_json = JSON_OBJECT(
      'decline_today', 4, 'decline_1d', 6, 'decline_1w', 6, 'decline_2w', 6,
      'decline_3w', 6, 'decline_4w', 5,
      'return_3m', 3, 'return_6m', 4, 'return_1y', 5,
      'return_2y', 4, 'return_3y', 4,
      'volatility_1y', 4, 'volatility_3y', 5,
      'sharpe_1y', 5, 'sharpe_3y', 5,
      'drawdown_1y', 4, 'drawdown_3y', 4,
      'rating_zhaoshang', 3, 'rating_shanghai_3y', 4,
      'rating_shanghai_5y', 3, 'rating_jian', 3,
      'rating_morningstar', 4, 'scale', 3
    ),
    validation_status = 'UNVERIFIED'
WHERE profile_name = '保守初始权重' AND version_no = 1;

-- 其他已有方案：补齐 decline_today（权重 0），保持 key 集与总和有效，不覆盖用户自定义权重。
UPDATE fund_score_profile
SET weights_json = JSON_INSERT(weights_json, '$.decline_today', 0),
    validation_status = 'UNVERIFIED'
WHERE (profile_name <> '保守初始权重' OR version_no <> 1)
  AND JSON_CONTAINS_PATH(weights_json, 'one', '$.decline_today') = 0;