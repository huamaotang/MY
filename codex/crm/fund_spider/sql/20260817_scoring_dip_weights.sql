USE fund;

-- 评分因子升级：新增短期跌幅因子（昨日/近1周/近2周/近3周/近4周），
-- 跌幅越大分值越高；其余因子权重重新分配，总和保持 100。
-- 需与 scoring.py DEFAULT_WEIGHTS、db.py 种子、Java FACTOR_KEYS、前端 SCORE_FACTORS 保持一致。

UPDATE fund_score_profile
SET weights_json = JSON_OBJECT(
      'decline_1d', 5, 'decline_1w', 5, 'decline_2w', 5,
      'decline_3w', 5, 'decline_4w', 5,
      'return_1m', 4, 'return_3m', 4, 'return_6m', 4,
      'return_1y', 5, 'return_2y', 4, 'return_3y', 4,
      'volatility_1y', 4, 'volatility_3y', 5,
      'sharpe_1y', 5, 'sharpe_3y', 5,
      'drawdown_1y', 4, 'drawdown_3y', 4,
      'rating_zhaoshang', 4, 'rating_shanghai_3y', 4,
      'rating_shanghai_5y', 3, 'rating_jian', 4,
      'rating_morningstar', 4, 'scale', 4
    ),
    validation_status = 'UNVERIFIED'
WHERE profile_name = '保守初始权重' AND version_no = 1;

-- 其他已有方案：补齐 5 个跌幅 key（权重 0），保持 key 集与总和有效，不覆盖用户自定义权重。
UPDATE fund_score_profile
SET weights_json = JSON_INSERT(
      weights_json,
      '$.decline_1d', 0,
      '$.decline_1w', 0,
      '$.decline_2w', 0,
      '$.decline_3w', 0,
      '$.decline_4w', 0
    ),
    validation_status = 'UNVERIFIED'
WHERE (profile_name <> '保守初始权重' OR version_no <> 1)
  AND JSON_CONTAINS_PATH(weights_json, 'one', '$.decline_1d') = 0;
