package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.BusinessException;
import com.example.crm.dto.portfolio.PortfolioHoldingBatchSummaryDto;
import com.example.crm.dto.portfolio.PortfolioHoldingCandidateDto;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmItemRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingImportPreviewResponse;
import com.example.crm.dto.portfolio.PortfolioHoldingImportRowDto;
import com.example.crm.dto.portfolio.UserFundHoldingDto;
import com.example.crm.dto.FundDailyValuationDto;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundHoldingCandidate;
import com.example.crm.entity.FundHoldingImportBatch;
import com.example.crm.entity.FundHoldingImportItem;
import com.example.crm.entity.UserFundHolding;
import com.example.crm.mapper.CfgFundMapper;
import com.example.crm.mapper.FundHoldingImportItemMapper;
import com.example.crm.mapper.FundHoldingImportMapper;
import com.example.crm.mapper.UserFundHoldingMapper;
import com.example.crm.service.IPortfolioHoldingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PortfolioHoldingServiceImpl implements IPortfolioHoldingService {
    private static final String SOURCE_LABEL = "alipay";
    private static final String PARSER_VERSION = "rapidocr-1";
    private static final int MAX_IMAGES = 3;

    private final FundHoldingImportMapper importMapper;
    private final FundHoldingImportItemMapper itemMapper;
    private final UserFundHoldingMapper holdingMapper;
    private final CfgFundMapper fundMapper;
    private final ObjectMapper objectMapper;
    private final PythonOcrClient pythonOcrClient;
    private final FundValuationService valuationService;

    public PortfolioHoldingServiceImpl(FundHoldingImportMapper importMapper,
                                       FundHoldingImportItemMapper itemMapper,
                                       UserFundHoldingMapper holdingMapper,
                                       CfgFundMapper fundMapper,
                                       ObjectMapper objectMapper,
                                       FundValuationService valuationService,
                                       @Value("${crm.python-ocr-script:fund_spider/portfolio_holding_ocr.py}") String ocrScriptPath,
                                       @Value("${crm.python-executable:python3}") String pythonExecutable) {
        this.importMapper = importMapper;
        this.itemMapper = itemMapper;
        this.holdingMapper = holdingMapper;
        this.fundMapper = fundMapper;
        this.objectMapper = objectMapper;
        this.valuationService = valuationService;
        this.pythonOcrClient = new PythonOcrClient(pythonExecutable, ocrScriptPath, objectMapper);
    }

    @Transactional
    @Override
    public PortfolioHoldingImportPreviewResponse preview(String ownerUsername, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new BusinessException("请先选择截图");
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException("最多一次上传3张截图");
        }

        List<File> tempFiles = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        try {
            for (MultipartFile image : images) {
                validateImage(image);
                tempFiles.add(writeTempFile(image));
                hashes.add(sha256(image));
            }
            PythonOcrResult ocrResult = pythonOcrClient.recognize(tempFiles);
            List<CfgFund> funds = fundMapper.selectList(new LambdaQueryWrapper<CfgFund>()
                    .select(CfgFund::getFundCode, CfgFund::getFundName));
            Map<String, CfgFund> exactMap = funds.stream()
                    .filter(fund -> hasText(fund.getFundName()))
                    .collect(Collectors.toMap(fund -> normalizeFundName(fund.getFundName()), fund -> fund, (left, right) -> left, LinkedHashMap::new));

            Map<String, PortfolioHoldingImportRowDto> rowsByFund = new LinkedHashMap<>();
            int duplicateCount = 0;
            for (PythonOcrImageResult image : ocrResult.getImages()) {
                for (PythonOcrRowResult row : image.getRows()) {
                    PortfolioHoldingImportRowDto dto = new PortfolioHoldingImportRowDto();
                    dto.setFundName(row.getFundName());
                    dto.setHoldingAmount(toDecimal(row.getHoldingAmount()));
                    dto.setHoldingProfit(toDecimal(row.getHoldingProfit()));
                    dto.setHoldingReturnRate(toDecimal(row.getHoldingReturnRate()));
                    dto.setHoldingCost(PortfolioHoldingCostCalculator.infer(
                            dto.getHoldingAmount(),
                            dto.getHoldingProfit(),
                            dto.getHoldingReturnRate()));
                    dto.setYesterdayProfit(toDecimal(row.getYesterdayProfit()));
                    dto.setTodayProfit(toDecimal(row.getTodayProfit()));
                    dto.setHoldingShares(toDecimal(row.getHoldingShares()));
                    dto.setCostNav(PortfolioHoldingCostCalculator.inferCostNav(
                            dto.getHoldingAmount(),
                            dto.getHoldingProfit(),
                            dto.getHoldingReturnRate(),
                            dto.getHoldingShares(),
                            dto.getHoldingCost()));
                    if (dto.getCostNav() == null) {
                        dto.setCostNav(toDecimal(row.getCostNav()));
                    }
                    dto.setScreenshotDate(LocalDate.now());
                    dto.setConfidence(row.getConfidence());
                    dto.setRawTexts(row.getRawTexts() == null ? Collections.emptyList() : row.getRawTexts());

                    List<PortfolioHoldingCandidateDto> candidates = rankCandidates(funds, row.getFundName());
                    dto.setCandidates(candidates);
                    if (!candidates.isEmpty() && candidates.get(0).getScore() != null && candidates.get(0).getScore() >= 80) {
                        dto.setFundCode(candidates.get(0).getFundCode());
                        dto.setFundName(candidates.get(0).getFundName());
                    } else {
                        String normalized = normalizeFundName(row.getFundName());
                        CfgFund exact = exactMap.get(normalized);
                        if (exact != null) {
                            dto.setFundCode(exact.getFundCode());
                            dto.setFundName(exact.getFundName());
                        }
                    }
                    String rowKey = normalizeFundName(dto.getFundName());
                    if (!hasText(rowKey)) {
                        rowKey = "unmatched-" + rowsByFund.size();
                    }
                    PortfolioHoldingImportRowDto existing = rowsByFund.get(rowKey);
                    if (existing == null) {
                        rowsByFund.put(rowKey, dto);
                    } else {
                        mergeImportRow(existing, dto);
                        duplicateCount++;
                    }
                }
            }
            List<PortfolioHoldingImportRowDto> rows = new ArrayList<>(rowsByFund.values());
            for (int index = 0; index < rows.size(); index++) {
                rows.get(index).setRowNo(index + 1);
            }
            if (rows.isEmpty()) {
                throw new BusinessException("未识别到基金持仓，请上传支付宝“我的持有”列表截图");
            }
            List<String> warnings = new ArrayList<>(ocrResult.getWarnings() == null
                    ? Collections.emptyList()
                    : ocrResult.getWarnings());
            if (duplicateCount > 0) {
                warnings.add("已合并滚动截图中的 " + duplicateCount + " 条重复基金");
            }

            FundHoldingImportBatch batch = new FundHoldingImportBatch();
            batch.setOwnerUsername(ownerUsername);
            batch.setSourceLabel(SOURCE_LABEL);
            batch.setStatus("PREVIEWED");
            batch.setScreenshotDate(LocalDate.now());
            batch.setImageCount(images.size());
            batch.setImageHashesJson(writeJson(hashes));
            batch.setRawOcrJson(writeJson(ocrResult));
            batch.setWarningsJson(writeJson(warnings));
            batch.setParserVersion(PARSER_VERSION);
            importMapper.insert(batch);

            List<FundHoldingImportItem> importItems = new ArrayList<>();
            for (PortfolioHoldingImportRowDto row : rows) {
                FundHoldingImportItem item = toImportItem(batch.getId(), row);
                importItems.add(item);
                itemMapper.insert(item);
            }

            PortfolioHoldingImportPreviewResponse response = new PortfolioHoldingImportPreviewResponse();
            response.setImportId(batch.getId());
            response.setSourceLabel(batch.getSourceLabel());
            response.setStatus(batch.getStatus());
            response.setScreenshotDate(batch.getScreenshotDate());
            response.setImageCount(batch.getImageCount());
            response.setImageHashes(hashes);
            response.setWarnings(warnings);
            response.setRows(rows);
            return response;
        } catch (IOException ex) {
            throw new BusinessException("截图识别失败：" + ex.getMessage());
        } finally {
            for (File file : tempFiles) {
                if (file != null && file.exists()) {
                    file.delete();
                }
            }
        }
    }

    @Transactional
    @Override
    public void confirm(String ownerUsername, Long importId, PortfolioHoldingConfirmRequest request) {
        FundHoldingImportBatch batch = loadBatch(ownerUsername, importId);
        if (!"PREVIEWED".equalsIgnoreCase(batch.getStatus()) && !"CONFIRMED".equalsIgnoreCase(batch.getStatus())) {
            throw new BusinessException("导入批次状态不可确认");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("请至少保留一条持仓");
        }

        LocalDate screenshotDate = request.getScreenshotDate() != null ? request.getScreenshotDate() : batch.getScreenshotDate();
        List<PortfolioHoldingConfirmItemRequest> items = request.getItems();
        List<FundHoldingImportItem> importItems = new ArrayList<>();
        for (PortfolioHoldingConfirmItemRequest row : items) {
            if (!hasText(row.getFundCode())) {
                throw new BusinessException("第 " + row.getRowNo() + " 行未绑定基金代码");
            }
            CfgFund fund = fundMapper.selectOne(new LambdaQueryWrapper<CfgFund>()
                    .eq(CfgFund::getFundCode, row.getFundCode())
                    .last("limit 1"));
            if (fund == null) {
                throw new BusinessException("第 " + row.getRowNo() + " 行基金代码无效");
            }
            UserFundHolding holding = new UserFundHolding();
            holding.setOwnerUsername(ownerUsername);
            holding.setFundCode(fund.getFundCode());
            holding.setFundName(fund.getFundName());
            holding.setHoldingAmount(row.getHoldingAmount());
            holding.setHoldingProfit(row.getHoldingProfit());
            holding.setHoldingReturnRate(row.getHoldingReturnRate());
            BigDecimal holdingCost = PortfolioHoldingCostCalculator.infer(
                    row.getHoldingAmount(),
                    row.getHoldingProfit(),
                    row.getHoldingReturnRate());
            BigDecimal costNav = PortfolioHoldingCostCalculator.inferCostNav(
                    row.getHoldingAmount(),
                    row.getHoldingProfit(),
                    row.getHoldingReturnRate(),
                    row.getHoldingShares(),
                    holdingCost);
            if (costNav == null) {
                costNav = row.getCostNav();
            }
            holding.setHoldingCost(holdingCost != null ? holdingCost : row.getHoldingCost());
            holding.setCostNav(costNav != null ? costNav : row.getCostNav());
            holding.setYesterdayProfit(row.getYesterdayProfit());
            holding.setTodayProfit(row.getTodayProfit());
            holding.setHoldingShares(row.getHoldingShares());
            holding.setScreenshotDate(screenshotDate);
            holding.setLatestImportId(importId);
            holding.setLatestImportAt(LocalDateTime.now());
            upsertHolding(holding);

            FundHoldingImportItem item = new FundHoldingImportItem();
            item.setImportId(importId);
            item.setRowNo(row.getRowNo());
            item.setFundCode(fund.getFundCode());
            item.setFundName(holding.getFundName());
            item.setHoldingAmount(row.getHoldingAmount());
            item.setHoldingProfit(row.getHoldingProfit());
            item.setHoldingReturnRate(row.getHoldingReturnRate());
            item.setHoldingCost(holding.getHoldingCost());
            item.setYesterdayProfit(row.getYesterdayProfit());
            item.setTodayProfit(row.getTodayProfit());
            item.setHoldingShares(row.getHoldingShares());
            item.setCostNav(row.getCostNav());
            item.setScreenshotDate(screenshotDate);
            item.setConfidence(row.getConfidence());
            item.setRawTextJson(writeJson(row.getRawTexts()));
            item.setStatus("CONFIRMED");
            importItems.add(item);
        }

        itemMapper.delete(new LambdaQueryWrapper<FundHoldingImportItem>().eq(FundHoldingImportItem::getImportId, importId));
        for (FundHoldingImportItem item : importItems) {
            itemMapper.insert(item);
        }

        batch.setStatus("CONFIRMED");
        batch.setConfirmedAt(LocalDateTime.now());
        batch.setScreenshotDate(screenshotDate);
        importMapper.updateById(batch);
    }

    @Override
    public Page<UserFundHoldingDto> holdings(String ownerUsername, long current, long size, String keyword) {
        Page<UserFundHolding> page = holdingMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<UserFundHolding>()
                .eq(UserFundHolding::getOwnerUsername, ownerUsername)
                .like(hasText(keyword), UserFundHolding::getFundName, keyword)
                .orderByDesc(UserFundHolding::getLatestImportAt)
                .orderByAsc(UserFundHolding::getFundCode));
        Page<UserFundHoldingDto> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        return result;
    }

    @Override
    public Page<PortfolioHoldingBatchSummaryDto> imports(String ownerUsername, long current, long size) {
        Page<FundHoldingImportBatch> page = importMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<FundHoldingImportBatch>()
                .eq(FundHoldingImportBatch::getOwnerUsername, ownerUsername)
                .orderByDesc(FundHoldingImportBatch::getCreatedAt));
        Page<PortfolioHoldingBatchSummaryDto> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Long> batchIds = page.getRecords().stream().map(FundHoldingImportBatch::getId).collect(Collectors.toList());
        Map<Long, Long> itemCounts = batchIds.isEmpty()
                ? Collections.emptyMap()
                : itemMapper.selectList(new LambdaQueryWrapper<FundHoldingImportItem>()
                        .in(FundHoldingImportItem::getImportId, batchIds))
                        .stream()
                        .collect(Collectors.groupingBy(FundHoldingImportItem::getImportId, Collectors.counting()));
        result.setRecords(page.getRecords().stream().map(batch -> {
            PortfolioHoldingBatchSummaryDto dto = new PortfolioHoldingBatchSummaryDto();
            dto.setId(batch.getId());
            dto.setStatus(batch.getStatus());
            dto.setSourceLabel(batch.getSourceLabel());
            dto.setScreenshotDate(batch.getScreenshotDate());
            dto.setImageCount(batch.getImageCount());
            dto.setItemCount(itemCounts.getOrDefault(batch.getId(), 0L).intValue());
            dto.setConfirmedAt(batch.getConfirmedAt());
            dto.setCreatedAt(batch.getCreatedAt());
            dto.setUpdatedAt(batch.getUpdatedAt());
            return dto;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public PortfolioHoldingImportPreviewResponse importDetail(String ownerUsername, Long importId) {
        FundHoldingImportBatch batch = loadBatch(ownerUsername, importId);
        List<FundHoldingImportItem> items = itemMapper.selectList(new LambdaQueryWrapper<FundHoldingImportItem>()
                .eq(FundHoldingImportItem::getImportId, importId)
                .orderByAsc(FundHoldingImportItem::getRowNo));
        PortfolioHoldingImportPreviewResponse response = new PortfolioHoldingImportPreviewResponse();
        response.setImportId(batch.getId());
        response.setSourceLabel(batch.getSourceLabel());
        response.setStatus(batch.getStatus());
        response.setScreenshotDate(batch.getScreenshotDate());
        response.setImageCount(batch.getImageCount());
        response.setImageHashes(parseJsonList(batch.getImageHashesJson()));
        response.setWarnings(parseJsonList(batch.getWarningsJson()));
        response.setRows(items.stream().map(this::toPreviewRow).collect(Collectors.toList()));
        return response;
    }

    private PortfolioHoldingImportRowDto toPreviewRow(FundHoldingImportItem item) {
        PortfolioHoldingImportRowDto dto = new PortfolioHoldingImportRowDto();
        dto.setRowNo(item.getRowNo());
        dto.setFundCode(item.getFundCode());
        dto.setFundName(item.getFundName());
        dto.setHoldingAmount(item.getHoldingAmount());
        dto.setHoldingProfit(item.getHoldingProfit());
        dto.setHoldingReturnRate(item.getHoldingReturnRate());
        dto.setHoldingCost(item.getHoldingCost());
        dto.setYesterdayProfit(item.getYesterdayProfit());
        dto.setTodayProfit(item.getTodayProfit());
        dto.setHoldingShares(item.getHoldingShares());
        dto.setCostNav(item.getCostNav());
        dto.setScreenshotDate(item.getScreenshotDate());
        dto.setConfidence(item.getConfidence());
        dto.setRawTexts(parseJsonList(item.getRawTextJson()));
        dto.setCandidates(Collections.emptyList());
        return dto;
    }

    private FundHoldingImportItem toImportItem(Long importId, PortfolioHoldingImportRowDto row) {
        FundHoldingImportItem item = new FundHoldingImportItem();
        item.setImportId(importId);
        item.setRowNo(row.getRowNo());
        item.setFundCode(row.getFundCode());
        item.setFundName(row.getFundName());
        item.setHoldingAmount(row.getHoldingAmount());
        item.setHoldingProfit(row.getHoldingProfit());
        item.setHoldingReturnRate(row.getHoldingReturnRate());
        item.setHoldingCost(row.getHoldingCost());
        item.setYesterdayProfit(row.getYesterdayProfit());
        item.setTodayProfit(row.getTodayProfit());
        item.setHoldingShares(row.getHoldingShares());
        item.setCostNav(row.getCostNav());
        item.setScreenshotDate(row.getScreenshotDate());
        item.setConfidence(row.getConfidence());
        item.setCandidateJson(writeJson(row.getCandidates()));
        item.setRawTextJson(writeJson(row.getRawTexts()));
        item.setStatus(hasText(row.getFundCode()) ? "MATCHED" : "PENDING");
        return item;
    }

    private UserFundHoldingDto toDto(UserFundHolding holding) {
        UserFundHoldingDto dto = new UserFundHoldingDto();
        dto.setId(holding.getId());
        dto.setOwnerUsername(holding.getOwnerUsername());
        dto.setFundCode(holding.getFundCode());
        dto.setFundName(holding.getFundName());
        dto.setHoldingAmount(holding.getHoldingAmount());
        dto.setHoldingProfit(holding.getHoldingProfit());
        dto.setHoldingReturnRate(holding.getHoldingReturnRate());
        dto.setHoldingCost(holding.getHoldingCost());
        dto.setYesterdayProfit(holding.getYesterdayProfit());
        dto.setTodayProfit(holding.getTodayProfit());
        dto.setHoldingShares(holding.getHoldingShares());
        dto.setCostNav(holding.getCostNav());
        FundDailyValuationDto valuation = valuationService.latest(holding.getFundCode());
        if (valuation != null) {
            BigDecimal estimatedDailyProfit = PortfolioValuationCalculator.estimatedDailyProfit(
                    holding.getHoldingAmount(),
                    holding.getHoldingShares(),
                    valuation.getBaseUnitNav(),
                    valuation.getEstimatedChangeRate());
            dto.setValuationDate(valuation.getValuationDate());
            dto.setHoldingReportDate(valuation.getHoldingReportDate());
            dto.setEstimatedChangeRate(valuation.getEstimatedChangeRate());
            dto.setEstimatedDailyProfit(estimatedDailyProfit);
            dto.setEstimatedHoldingAmount(PortfolioValuationCalculator.estimatedHoldingAmount(
                    holding.getHoldingAmount(), estimatedDailyProfit));
            dto.setEstimatedUnitNav(valuation.getEstimatedUnitNav());
            BigDecimal estimatedCumulativeChangeRate =
                    PortfolioValuationCalculator.estimatedCumulativeChangeRate(
                            holding.getCostNav(), valuation.getEstimatedUnitNav());
            dto.setEstimatedCumulativeChangeRate(estimatedCumulativeChangeRate);
            dto.setEstimatedCumulativeProfit(PortfolioValuationCalculator.estimatedCumulativeProfit(
                    holding.getHoldingShares(),
                    holding.getCostNav(),
                    valuation.getEstimatedUnitNav(),
                    holding.getHoldingCost(),
                    estimatedCumulativeChangeRate));
            dto.setValuationCoverageRate(valuation.getQuoteCoverageRate());
            dto.setValuationUpdatedAt(valuation.getQuoteUpdatedAt());
        }
        dto.setScreenshotDate(holding.getScreenshotDate());
        dto.setLatestImportId(holding.getLatestImportId());
        dto.setLatestImportAt(holding.getLatestImportAt());
        dto.setCreatedAt(holding.getCreatedAt());
        dto.setUpdatedAt(holding.getUpdatedAt());
        return dto;
    }

    private FundHoldingImportBatch loadBatch(String ownerUsername, Long importId) {
        FundHoldingImportBatch batch = importMapper.selectOne(new LambdaQueryWrapper<FundHoldingImportBatch>()
                .eq(FundHoldingImportBatch::getId, importId)
                .eq(FundHoldingImportBatch::getOwnerUsername, ownerUsername)
                .last("limit 1"));
        if (batch == null) {
            throw new BusinessException("导入批次不存在");
        }
        return batch;
    }

    private List<PortfolioHoldingCandidateDto> rankCandidates(List<CfgFund> funds, String fundName) {
        if (!hasText(fundName)) {
            return Collections.emptyList();
        }
        String normalized = normalizeFundName(fundName);
        List<PortfolioHoldingCandidateDto> candidates = new ArrayList<>();
        for (CfgFund fund : funds) {
            if (!hasText(fund.getFundName()) || !hasText(fund.getFundCode())) {
                continue;
            }
            String candidateName = normalizeFundName(fund.getFundName());
            int score = scoreCandidate(normalized, candidateName);
            if (score >= 0) {
                PortfolioHoldingCandidateDto candidate = new PortfolioHoldingCandidateDto();
                candidate.setFundCode(fund.getFundCode());
                candidate.setFundName(fund.getFundName());
                candidate.setScore(score);
                candidates.add(candidate);
            }
        }
        candidates.sort((left, right) -> Integer.compare(right.getScore(), left.getScore()));
        return candidates.stream().limit(5).collect(Collectors.toList());
    }

    private int scoreCandidate(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return -1;
        }
        if (left.equals(right)) {
            return 100;
        }
        if (left.contains(right) || right.contains(left)) {
            return 90 - Math.abs(left.length() - right.length());
        }
        int distance = levenshtein(left, right);
        int base = Math.max(left.length(), right.length());
        if (base == 0) {
            return -1;
        }
        int score = 80 - distance * 4;
        return score > 0 ? score : -1;
    }

    private int levenshtein(String left, String right) {
        int[] prev = new int[right.length() + 1];
        int[] curr = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[right.length()];
    }

    private BigDecimal toDecimal(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            String cleaned = value.replace(",", "").replace("%", "").trim();
            if (cleaned.startsWith("+")) {
                cleaned = cleaned.substring(1);
            }
            return new BigDecimal(cleaned);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeFundName(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\s\\p{Punct}]", "").replace("（", "").replace("）", "").replace("(", "").replace(")", "").toLowerCase(Locale.ROOT);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("截图文件为空");
        }
        String contentType = file.getContentType();
        String original = file.getOriginalFilename();
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = original == null ? "" : original.toLowerCase(Locale.ROOT);
        boolean supportedType = "image/jpeg".equals(type) || "image/png".equals(type);
        boolean supportedName = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
        if (!supportedType && !supportedName) {
            throw new BusinessException("仅支持 JPG、JPEG、PNG 截图");
        }
    }

    private File writeTempFile(MultipartFile file) throws IOException {
        String suffix = "image/png".equalsIgnoreCase(file.getContentType()) ? ".png" : ".jpg";
        Path temp = Files.createTempFile("fund-holding-", suffix);
        Files.copy(file.getInputStream(), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return temp.toFile();
    }

    private void mergeImportRow(PortfolioHoldingImportRowDto target, PortfolioHoldingImportRowDto source) {
        if (!hasText(target.getFundCode()) && hasText(source.getFundCode())) {
            target.setFundCode(source.getFundCode());
            target.setFundName(source.getFundName());
            target.setCandidates(source.getCandidates());
        }
        if (target.getHoldingAmount() == null) target.setHoldingAmount(source.getHoldingAmount());
        if (target.getHoldingProfit() == null) target.setHoldingProfit(source.getHoldingProfit());
        if (target.getHoldingReturnRate() == null) target.setHoldingReturnRate(source.getHoldingReturnRate());
        BigDecimal holdingCost = PortfolioHoldingCostCalculator.infer(
                target.getHoldingAmount(),
                target.getHoldingProfit(),
                target.getHoldingReturnRate());
        target.setHoldingCost(holdingCost != null ? holdingCost : source.getHoldingCost());
        if (target.getYesterdayProfit() == null) target.setYesterdayProfit(source.getYesterdayProfit());
        if (target.getTodayProfit() == null) target.setTodayProfit(source.getTodayProfit());
        if (target.getHoldingShares() == null) target.setHoldingShares(source.getHoldingShares());
        BigDecimal costNav = PortfolioHoldingCostCalculator.inferCostNav(
                target.getHoldingAmount(),
                target.getHoldingProfit(),
                target.getHoldingReturnRate(),
                target.getHoldingShares(),
                target.getHoldingCost());
        target.setCostNav(costNav != null ? costNav : source.getCostNav());
        if (target.getConfidence() == null
                || source.getConfidence() != null && source.getConfidence().compareTo(target.getConfidence()) > 0) {
            target.setConfidence(source.getConfidence());
        }
        List<String> rawTexts = new ArrayList<>(target.getRawTexts() == null
                ? Collections.emptyList()
                : target.getRawTexts());
        if (source.getRawTexts() != null) {
            for (String rawText : source.getRawTexts()) {
                if (!rawTexts.contains(rawText)) {
                    rawTexts.add(rawText);
                }
            }
        }
        target.setRawTexts(rawTexts);
    }

    private String sha256(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file.getBytes());
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception ex) {
            throw new IOException("图片哈希计算失败", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException("保存识别结果失败");
        }
    }

    private List<String> parseJsonList(String json) {
        if (!hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void upsertHolding(UserFundHolding holding) {
        UserFundHolding existing = holdingMapper.selectOne(new LambdaQueryWrapper<UserFundHolding>()
                .eq(UserFundHolding::getOwnerUsername, holding.getOwnerUsername())
                .eq(UserFundHolding::getFundCode, holding.getFundCode())
                .last("limit 1"));
        if (existing == null) {
            holdingMapper.insert(holding);
            return;
        }
        holding.setId(existing.getId());
        holding.setCreatedAt(existing.getCreatedAt());
        holdingMapper.updateById(holding);
    }

    private static final class PythonOcrClient {
        private final String pythonExecutable;
        private final String scriptPath;
        private final ObjectMapper objectMapper;

        private PythonOcrClient(String pythonExecutable, String scriptPath, ObjectMapper objectMapper) {
            this.pythonExecutable = pythonExecutable;
            this.scriptPath = scriptPath;
            this.objectMapper = objectMapper;
        }

        private PythonOcrResult recognize(List<File> images) throws IOException {
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(resolveScriptPath().toString());
            command.add("--json");
            for (File image : images) {
                command.add("--image");
                command.add(image.getAbsolutePath());
            }
            Path outputFile = Files.createTempFile("fund-holding-ocr-", ".json");
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                builder.redirectOutput(outputFile.toFile());
                Process process = builder.start();
                boolean completed = process.waitFor(120, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    throw new IOException("OCR识别超时，请减少图片数量后重试");
                }
                String output = new String(Files.readAllBytes(outputFile), java.nio.charset.StandardCharsets.UTF_8);
                int exit = process.exitValue();
                if (exit != 0) {
                    throw new IOException(output.isEmpty() ? "OCR 识别失败" : output.trim());
                }
                return objectMapper.readValue(output, PythonOcrResult.class);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("OCR 识别中断", ex);
            } finally {
                Files.deleteIfExists(outputFile);
            }
        }

        private Path resolveScriptPath() throws IOException {
            Path configured = Paths.get(scriptPath);
            if (configured.isAbsolute() && Files.isRegularFile(configured)) {
                return configured;
            }
            Path workingDirectory = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            Path current = workingDirectory;
            for (int depth = 0; depth < 5 && current != null; depth++) {
                Path candidate = current.resolve(configured).normalize();
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
            throw new IOException("找不到OCR脚本：" + scriptPath);
        }
    }

    private static class PythonOcrResult {
        private List<PythonOcrImageResult> images = Collections.emptyList();
        private List<String> warnings = Collections.emptyList();

        public List<PythonOcrImageResult> getImages() {
            return images;
        }

        public void setImages(List<PythonOcrImageResult> images) {
            this.images = images;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }
    }

    private static class PythonOcrImageResult {
        private List<PythonOcrRowResult> rows = Collections.emptyList();

        public List<PythonOcrRowResult> getRows() {
            return rows;
        }

        public void setRows(List<PythonOcrRowResult> rows) {
            this.rows = rows;
        }
    }

    private static class PythonOcrRowResult {
        private String fundName;
        private String holdingAmount;
        private String holdingProfit;
        private String holdingReturnRate;
        private String yesterdayProfit;
        private String todayProfit;
        private String holdingShares;
        private String costNav;
        private BigDecimal confidence;
        private List<String> rawTexts = Collections.emptyList();

        public String getFundName() {
            return fundName;
        }

        public void setFundName(String fundName) {
            this.fundName = fundName;
        }

        public String getHoldingAmount() {
            return holdingAmount;
        }

        public void setHoldingAmount(String holdingAmount) {
            this.holdingAmount = holdingAmount;
        }

        public String getHoldingProfit() {
            return holdingProfit;
        }

        public void setHoldingProfit(String holdingProfit) {
            this.holdingProfit = holdingProfit;
        }

        public String getHoldingReturnRate() {
            return holdingReturnRate;
        }

        public void setHoldingReturnRate(String holdingReturnRate) {
            this.holdingReturnRate = holdingReturnRate;
        }

        public String getYesterdayProfit() {
            return yesterdayProfit;
        }

        public void setYesterdayProfit(String yesterdayProfit) {
            this.yesterdayProfit = yesterdayProfit;
        }

        public String getTodayProfit() {
            return todayProfit;
        }

        public void setTodayProfit(String todayProfit) {
            this.todayProfit = todayProfit;
        }

        public String getHoldingShares() {
            return holdingShares;
        }

        public void setHoldingShares(String holdingShares) {
            this.holdingShares = holdingShares;
        }

        public String getCostNav() {
            return costNav;
        }

        public void setCostNav(String costNav) {
            this.costNav = costNav;
        }

        public BigDecimal getConfidence() {
            return confidence;
        }

        public void setConfidence(BigDecimal confidence) {
            this.confidence = confidence;
        }

        public List<String> getRawTexts() {
            return rawTexts;
        }

        public void setRawTexts(List<String> rawTexts) {
            this.rawTexts = rawTexts;
        }
    }
}
