package com.example.crm.service.impl;

import com.example.crm.common.BusinessException;
import com.example.crm.dto.score.FundScoreBacktestDto;
import com.example.crm.dto.score.FundScoreComponentDto;
import com.example.crm.dto.score.FundScoreDetailDto;
import com.example.crm.dto.score.FundScoreJobDto;
import com.example.crm.dto.score.FundScoreProfileDto;
import com.example.crm.dto.score.FundScoreProfileSaveRequest;
import com.example.crm.dto.score.FundScoreSummaryDto;
import com.example.crm.service.IFundScoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FundScoreServiceImpl implements IFundScoreService {
    private static final Set<String> FACTOR_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "decline_today", "decline_1d", "decline_1w", "decline_2w", "decline_3w", "decline_4w",
            "return_3m", "return_6m", "return_1y", "return_2y", "return_3y",
            "volatility_1y", "volatility_3y", "sharpe_1y", "sharpe_3y",
            "drawdown_1y", "drawdown_3y", "rating_zhaoshang", "rating_shanghai_3y",
            "rating_shanghai_5y", "rating_jian", "rating_morningstar", "scale"
    )));
    private static final String DISCLAIMER =
            "评分和盈利概率仅基于历史数据与同类比较，不代表收益承诺；未通过回测的方案不展示盈利概率。";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FundScoreServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, FundScoreSummaryDto> latestSummaries(List<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(fundCodes.size(), "?"));
        String sql = "SELECT r.*,p.profile_name,p.version_no,p.validation_status " +
                "FROM fund_score_result r " +
                "JOIN fund_score_profile p ON p.id=r.profile_id AND p.is_active=1 " +
                "JOIN (SELECT r2.fund_code,MAX(r2.as_of_date) as_of_date " +
                "FROM fund_score_result r2 JOIN fund_score_profile p2 ON p2.id=r2.profile_id AND p2.is_active=1 " +
                "WHERE r2.fund_code IN (" + placeholders + ") GROUP BY r2.fund_code) latest " +
                "ON latest.fund_code=r.fund_code AND latest.as_of_date=r.as_of_date " +
                "WHERE r.fund_code IN (" + placeholders + ")";
        List<Object> params = new ArrayList<>();
        params.addAll(fundCodes);
        params.addAll(fundCodes);
        Map<String, FundScoreSummaryDto> result = new HashMap<>();
        List<Map.Entry<String, FundScoreSummaryDto>> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new java.util.AbstractMap.SimpleEntry<>(
                        rs.getString("fund_code"),
                        mapSummary(rs)
                ),
                params.toArray()
        );
        for (Map.Entry<String, FundScoreSummaryDto> row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }

    @Override
    public FundScoreDetailDto detail(String fundCode) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT r.*,p.profile_name,p.version_no,p.validation_status " +
                            "FROM fund_score_result r JOIN fund_score_profile p ON p.id=r.profile_id AND p.is_active=1 " +
                            "WHERE r.fund_code=? ORDER BY r.as_of_date DESC LIMIT 1",
                    (rs, rowNum) -> {
                        FundScoreDetailDto detail = new FundScoreDetailDto();
                        detail.setSummary(mapSummary(rs));
                        detail.setComponents(readComponents(rs.getString("components_json")));
                        detail.setDisclaimer(DISCLAIMER);
                        return detail;
                    },
                    fundCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    @Override
    public List<FundScoreProfileDto> profiles() {
        return jdbcTemplate.query(
                "SELECT * FROM fund_score_profile ORDER BY is_active DESC,created_at DESC,id DESC",
                (rs, rowNum) -> mapProfile(rs)
        );
    }

    @Override
    @Transactional
    public FundScoreProfileDto createProfile(FundScoreProfileSaveRequest request, String username) {
        String name = validateProfile(request);
        Integer version = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM fund_score_profile WHERE profile_name=?",
                Integer.class,
                name
        );
        jdbcTemplate.update(
                "INSERT INTO fund_score_profile " +
                        "(profile_name,version_no,status,source_type,target_months,weights_json,validation_status,is_active,created_by) " +
                        "VALUES (?,?, 'DRAFT','MANUAL',12,?,'UNVERIFIED',0,?)",
                name,
                version,
                writeJson(request.getWeights()),
                username
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return findProfile(id);
    }

    @Override
    @Transactional
    public FundScoreProfileDto updateProfile(Long id, FundScoreProfileSaveRequest request) {
        FundScoreProfileDto existing = findProfile(id);
        if (!"DRAFT".equals(existing.getStatus()) && !"FAILED".equals(existing.getValidationStatus())) {
            throw new BusinessException("仅草稿或回测未通过的方案可以修改");
        }
        String name = validateProfile(request);
        jdbcTemplate.update(
                "UPDATE fund_score_profile SET profile_name=?,weights_json=?,validation_status='UNVERIFIED'," +
                        "calibration_json=NULL,status='DRAFT' WHERE id=?",
                name,
                writeJson(request.getWeights()),
                id
        );
        return findProfile(id);
    }

    @Override
    @Transactional
    public FundScoreJobDto enqueueBacktest(Long profileId, String username) {
        findProfile(profileId);
        jdbcTemplate.update(
                "UPDATE fund_score_profile SET status='PENDING_BACKTEST',validation_status='UNVERIFIED' WHERE id=?",
                profileId
        );
        return enqueue("BACKTEST", profileId, username);
    }

    @Override
    public FundScoreJobDto enqueueRecommendation(String username) {
        return enqueue("RECOMMEND", null, username);
    }

    @Override
    public List<FundScoreJobDto> jobs() {
        return jdbcTemplate.query(
                "SELECT * FROM fund_score_job ORDER BY created_at DESC,id DESC LIMIT 100",
                (rs, rowNum) -> mapJob(rs)
        );
    }

    @Override
    public FundScoreBacktestDto latestBacktest(Long profileId) {
        findProfile(profileId);
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM fund_score_backtest WHERE profile_id=? ORDER BY created_at DESC,id DESC LIMIT 1",
                    (rs, rowNum) -> mapBacktest(rs),
                    profileId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    @Override
    @Transactional
    public void activate(Long profileId, String username) {
        FundScoreProfileDto profile = findProfile(profileId);
        if (!"SEED".equals(profile.getSourceType()) && !"PASSED".equals(profile.getValidationStatus())) {
            throw new BusinessException("方案必须通过历史回测门槛后才能启用");
        }
        jdbcTemplate.update(
                "UPDATE fund_score_profile SET is_active=0,status=CASE WHEN status='ACTIVE' THEN 'ARCHIVED' ELSE status END " +
                        "WHERE is_active=1"
        );
        jdbcTemplate.update(
                "UPDATE fund_score_profile SET is_active=1,status='ACTIVE',approved_by=?,approved_at=NOW() WHERE id=?",
                username,
                profileId
        );
        enqueue("CURRENT_SCORE", profileId, username);
    }

    private FundScoreJobDto enqueue(String type, Long profileId, String username) {
        jdbcTemplate.update(
                "INSERT INTO fund_score_job (job_type,profile_id,status,requested_by) VALUES (?,?,'PENDING',?)",
                type,
                profileId,
                username
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return jdbcTemplate.queryForObject(
                "SELECT * FROM fund_score_job WHERE id=?",
                (rs, rowNum) -> mapJob(rs),
                id
        );
    }

    private String validateProfile(FundScoreProfileSaveRequest request) {
        if (request == null || request.getProfileName() == null || request.getProfileName().trim().isEmpty()) {
            throw new BusinessException("方案名称不能为空");
        }
        if (request.getWeights() == null || !FACTOR_KEYS.equals(request.getWeights().keySet())) {
            throw new BusinessException("评分因子不完整或包含未知因子");
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : request.getWeights().entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 100) {
                throw new BusinessException("权重必须是0到100之间的整数");
            }
            total += entry.getValue();
        }
        if (total != 100) {
            throw new BusinessException("全部权重之和必须等于100");
        }
        return request.getProfileName().trim();
    }

    private FundScoreProfileDto findProfile(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM fund_score_profile WHERE id=?",
                    (rs, rowNum) -> mapProfile(rs),
                    id
            );
        } catch (EmptyResultDataAccessException ignored) {
            throw new BusinessException("评分方案不存在");
        }
    }

    private FundScoreSummaryDto mapSummary(ResultSet rs) throws SQLException {
        FundScoreSummaryDto value = new FundScoreSummaryDto();
        value.setProfileId(rs.getLong("profile_id"));
        value.setProfileName(rs.getString("profile_name"));
        value.setProfileVersion(rs.getInt("version_no"));
        value.setValidationStatus(rs.getString("validation_status"));
        value.setAsOfDate(rs.getString("as_of_date"));
        value.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        value.setTotalScore(rs.getBigDecimal("total_score"));
        value.setProfitProbability(rs.getBigDecimal("profit_probability"));
        value.setConfidence(rs.getString("confidence"));
        value.setDataCoverage(rs.getBigDecimal("data_coverage"));
        value.setComparisonGroup(rs.getString("comparison_group"));
        value.setCategoryRank(nullableInt(rs, "category_rank"));
        value.setCategoryCount(nullableInt(rs, "category_count"));
        value.setMethodologyVersion(rs.getString("methodology_version"));
        return value;
    }

    private FundScoreProfileDto mapProfile(ResultSet rs) throws SQLException {
        FundScoreProfileDto value = new FundScoreProfileDto();
        value.setId(rs.getLong("id"));
        value.setProfileName(rs.getString("profile_name"));
        value.setVersionNo(rs.getInt("version_no"));
        value.setStatus(rs.getString("status"));
        value.setSourceType(rs.getString("source_type"));
        value.setTargetMonths(rs.getInt("target_months"));
        value.setWeights(readWeights(rs.getString("weights_json")));
        value.setValidationStatus(rs.getString("validation_status"));
        value.setActive(rs.getBoolean("is_active"));
        value.setCreatedBy(rs.getString("created_by"));
        value.setApprovedBy(rs.getString("approved_by"));
        value.setApprovedAt(toLocalDateTime(rs.getTimestamp("approved_at")));
        value.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        value.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return value;
    }

    private FundScoreJobDto mapJob(ResultSet rs) throws SQLException {
        FundScoreJobDto value = new FundScoreJobDto();
        value.setId(rs.getLong("id"));
        value.setJobType(rs.getString("job_type"));
        long profileId = rs.getLong("profile_id");
        value.setProfileId(rs.wasNull() ? null : profileId);
        value.setStatus(rs.getString("status"));
        value.setRequestedBy(rs.getString("requested_by"));
        value.setMessage(rs.getString("message"));
        value.setStartedAt(toLocalDateTime(rs.getTimestamp("started_at")));
        value.setFinishedAt(toLocalDateTime(rs.getTimestamp("finished_at")));
        value.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        value.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return value;
    }

    private FundScoreBacktestDto mapBacktest(ResultSet rs) throws SQLException {
        FundScoreBacktestDto value = new FundScoreBacktestDto();
        value.setId(rs.getLong("id"));
        value.setProfileId(rs.getLong("profile_id"));
        value.setTrainStartDate(rs.getString("train_start_date"));
        value.setTrainEndDate(rs.getString("train_end_date"));
        value.setTestStartDate(rs.getString("test_start_date"));
        value.setTestEndDate(rs.getString("test_end_date"));
        value.setSampleCount(rs.getInt("sample_count"));
        value.setFoldCount(rs.getInt("fold_count"));
        value.setAuc(rs.getBigDecimal("auc"));
        value.setBrierScore(rs.getBigDecimal("brier_score"));
        value.setBaselineBrierScore(rs.getBigDecimal("baseline_brier_score"));
        value.setTop20WinRate(rs.getBigDecimal("top20_win_rate"));
        value.setBaselineWinRate(rs.getBigDecimal("baseline_win_rate"));
        value.setWinRateLift(rs.getBigDecimal("win_rate_lift"));
        value.setPassed(rs.getBoolean("passed"));
        value.setLimitationsJson(rs.getString("limitations_json"));
        value.setMetricsJson(rs.getString("metrics_json"));
        value.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return value;
    }

    private Map<String, Integer> readWeights(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException("评分权重数据损坏");
        }
    }

    private List<FundScoreComponentDto> readComponents(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FundScoreComponentDto>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException("评分分项数据损坏");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("评分权重无法序列化");
        }
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
