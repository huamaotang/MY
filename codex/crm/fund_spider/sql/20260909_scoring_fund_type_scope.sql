USE fund;

-- 评分方案区分基金类型：新增 fund_type_scope 列，创建"债类配置"和"股票类配置"两套方案。
-- 债类配置（scope: 债券型,货币型）: 取消当日估值因子（decline_today 权重=0），释放的4分加权到收益因子。
-- 股票类配置（scope: *）: 原"保守初始权重"方案改名，保留全部因子。
-- 需与 scoring.py BOND_WEIGHTS、db.py 种子、Java FACTOR_KEYS、前端 SCORE_FACTORS 保持一致。

ALTER TABLE fund_score_profile ADD COLUMN fund_type_scope VARCHAR(100) NULL COMMENT '适用基金类型范围，*表示全部' AFTER target_months;

-- 更新已有方案：重命名为"股票类配置"，scope 设为"*"
UPDATE fund_score_profile
SET profile_name = '股票类配置',
    fund_type_scope = '*',
    validation_status = 'UNVERIFIED'
WHERE profile_name = '保守初始权重' AND version_no = 1;

-- 插入"债类配置"方案：decline_today 权重=0，释放的4分加权到收益因子
INSERT INTO fund_score_profile (
  profile_name, version_no, status, source_type, target_months, fund_type_scope,
  weights_json, validation_status, is_active, created_by
)
SELECT
  '债类配置', 1, 'ACTIVE', 'SEED', 12, '债券型,货币型',
  JSON_OBJECT(
    'decline_today', 0, 'decline_1d', 6, 'decline_1w', 6, 'decline_2w', 6,
    'decline_3w', 6, 'decline_4w', 5,
    'return_3m', 4, 'return_6m', 5, 'return_1y', 6,
    'return_2y', 5, 'return_3y', 4,
    'volatility_1y', 4, 'volatility_3y', 5,
    'sharpe_1y', 5, 'sharpe_3y', 5,
    'drawdown_1y', 4, 'drawdown_3y', 4,
    'rating_zhaoshang', 3, 'rating_shanghai_3y', 4,
    'rating_shanghai_5y', 3, 'rating_jian', 3,
    'rating_morningstar', 4, 'scale', 3
  ),
  'UNVERIFIED', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM fund_score_profile WHERE profile_name = '债类配置' AND version_no = 1);

-- 其他已有方案：补齐 decline_today（权重 0），保持 key 集与总和有效，不覆盖用户自定义权重。
UPDATE fund_score_profile
SET weights_json = JSON_INSERT(weights_json, '$.decline_today', 0),
    validation_status = 'UNVERIFIED'
WHERE (profile_name NOT IN ('债类配置', '股票类配置') OR version_no <> 1)
  AND JSON_CONTAINS_PATH(weights_json, 'one', '$.decline_today') = 0;
