USE fund;

-- 评分因子调整：删除"近1月收益"（return_1m）因子。
-- 释放的 4 分加权到短期跌幅块（decline_1d/1w/2w/3w 各 +1），总和保持 100。
-- 需与 scoring.py DEFAULT_WEIGHTS、db.py 种子、Java FACTOR_KEYS、前端 SCORE_FACTORS 保持一致。

UPDATE fund_score_profile
SET weights_json = JSON_OBJECT(
      'decline_1d', 6, 'decline_1w', 6, 'decline_2w', 6,
      'decline_3w', 6, 'decline_4w', 5,
      'return_3m', 4, 'return_6m', 4,
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

-- 其他已有方案：删除 return_1m，把其权重并入 decline_4w，保持 key 集与总和有效。
UPDATE fund_score_profile
SET weights_json = JSON_REMOVE(
      JSON_SET(weights_json, '$.decline_4w', JSON_EXTRACT(weights_json, '$.decline_4w') + JSON_EXTRACT(weights_json, '$.return_1m')),
      '$.return_1m'
    ),
    validation_status = 'UNVERIFIED'
WHERE (profile_name <> '保守初始权重' OR version_no <> 1)
  AND JSON_CONTAINS_PATH(weights_json, 'one', '$.return_1m') = 1;
