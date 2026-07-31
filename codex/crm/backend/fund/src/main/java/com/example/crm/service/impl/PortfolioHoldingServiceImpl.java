package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.BusinessException;
import com.example.crm.dto.portfolio.PortfolioHoldingBatchSummaryDto;
import com.example.crm.dto.portfolio.PortfolioHoldingCandidateDto;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmResponse;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmItemRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingImportPreviewResponse;
import com.example.crm.dto.portfolio.PortfolioHoldingImportRowDto;
import com.example.crm.dto.portfolio.PortfolioAccountSummaryDto;
import com.example.crm.dto.portfolio.PortfolioOverviewDto;
import com.example.crm.dto.portfolio.PortfolioTradeAdjustmentDto;
import com.example.crm.dto.portfolio.PortfolioTradeMappingRequest;
import com.example.crm.dto.portfolio.UserFundHoldingDto;
import com.example.crm.dto.FundDailyValuationDto;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundHoldingImportBatch;
import com.example.crm.entity.FundHoldingImportItem;
import com.example.crm.entity.FundHoldingTradeImportItem;
import com.example.crm.entity.UserFundHolding;
import com.example.crm.mapper.CfgFundMapper;
import com.example.crm.mapper.FundHoldingImportItemMapper;
import com.example.crm.mapper.FundHoldingImportMapper;
import com.example.crm.mapper.FundHoldingTradeImportItemMapper;
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
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PortfolioHoldingServiceImpl implements IPortfolioHoldingService {
    private static final String PARSER_VERSION = "rapidocr-1";
    private static final String IMPORT_HOLDING = "holding";
    private static final String IMPORT_TRADE = "trade";
    private static final int MAX_IMAGES = 3;

    private final FundHoldingImportMapper importMapper;
    private final FundHoldingImportItemMapper itemMapper;
    private final FundHoldingTradeImportItemMapper tradeItemMapper;
    private final UserFundHoldingMapper holdingMapper;
    private final CfgFundMapper fundMapper;
    private final ObjectMapper objectMapper;
    private final PythonOcrClient pythonOcrClient;
    private final FundValuationService valuationService;

    public PortfolioHoldingServiceImpl(FundHoldingImportMapper importMapper,
                                       FundHoldingImportItemMapper itemMapper,
                                       FundHoldingTradeImportItemMapper tradeItemMapper,
                                       UserFundHoldingMapper holdingMapper,
                                       CfgFundMapper fundMapper,
                                       ObjectMapper objectMapper,
                                       FundValuationService valuationService,
                                       @Value("${crm.python-ocr-script:fund_spider/tools/portfolio_holding_ocr.py}") String ocrScriptPath,
                                       @Value("${crm.python-executable:python3}") String pythonExecutable) {
        this.importMapper = importMapper;
        this.itemMapper = itemMapper;
        this.tradeItemMapper = tradeItemMapper;
        this.holdingMapper = holdingMapper;
        this.fundMapper = fundMapper;
        this.objectMapper = objectMapper;
        this.valuationService = valuationService;
        this.pythonOcrClient = new PythonOcrClient(pythonExecutable, ocrScriptPath, objectMapper);
    }

    @Transactional
    @Override
    public PortfolioHoldingImportPreviewResponse preview(String ownerUsername, String sourceLabel,
                                                         String importType, List<MultipartFile> images) {
        String normalizedSource = normalizeSource(sourceLabel);
        String normalizedImportType = normalizeImportType(importType);
        if (images == null || images.isEmpty()) {
            throw new BusinessException("请先选择截图");
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException("最多一次上传3张截图");
        }

        List<File> tempFiles = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            Set<String> confirmedImageHashes = IMPORT_TRADE.equals(normalizedImportType)
                    ? confirmedTradeImageHashes(ownerUsername, normalizedSource)
                    : Collections.emptySet();
            Set<String> uploadImageHashes = new HashSet<>();
            for (MultipartFile image : images) {
                validateImage(image);
                String hash = sha256(image);
                if (!uploadImageHashes.add(hash)) {
                    warnings.add("已跳过本批次重复截图：" + safeFilename(image.getOriginalFilename()));
                    continue;
                }
                if (confirmedImageHashes.contains(hash)) {
                    warnings.add("已跳过重复交易截图：" + safeFilename(image.getOriginalFilename()));
                    continue;
                }
                tempFiles.add(writeTempFile(image));
                hashes.add(hash);
            }
            if (tempFiles.isEmpty()) {
                throw new BusinessException("所选交易截图均已导入，无需重复处理");
            }
            PythonOcrResult ocrResult = pythonOcrClient.recognize(
                    tempFiles, normalizedSource, normalizedImportType);
            if (ocrResult.getWarnings() != null) {
                warnings.addAll(ocrResult.getWarnings());
            }
            if (IMPORT_TRADE.equals(normalizedImportType)) {
                return createTradePreview(ownerUsername, normalizedSource, hashes,
                        warnings, ocrResult, tempFiles.size());
            }
            return createHoldingPreview(ownerUsername, normalizedSource, hashes,
                    warnings, ocrResult, tempFiles.size());
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

    private PortfolioHoldingImportPreviewResponse createHoldingPreview(
            String ownerUsername, String sourceLabel, List<String> hashes,
            List<String> warnings, PythonOcrResult ocrResult, int imageCount) {
        List<CfgFund> funds = fundMapper.selectList(new LambdaQueryWrapper<CfgFund>()
                .select(CfgFund::getFundCode, CfgFund::getFundName));
        Map<String, CfgFund> exactMap = funds.stream()
                .filter(fund -> hasText(fund.getFundName()))
                .collect(Collectors.toMap(
                        fund -> normalizeFundName(fund.getFundName()),
                        fund -> fund,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, PortfolioHoldingImportRowDto> rowsByFund = new LinkedHashMap<>();
        int duplicateCount = 0;
        for (PythonOcrImageResult image : safeOcrImages(ocrResult)) {
            for (PythonOcrRowResult row : safeOcrRows(image)) {
                PortfolioHoldingImportRowDto dto = new PortfolioHoldingImportRowDto();
                dto.setFundName(row.getFundName());
                dto.setHoldingAmount(toDecimal(row.getHoldingAmount()));
                dto.setHoldingProfit(toDecimal(row.getHoldingProfit()));
                dto.setHoldingReturnRate(toDecimal(row.getHoldingReturnRate()));
                dto.setHoldingCost(PortfolioHoldingCostCalculator.infer(
                        dto.getHoldingAmount(), dto.getHoldingProfit(), dto.getHoldingReturnRate()));
                dto.setYesterdayProfit(toDecimal(row.getYesterdayProfit()));
                dto.setTodayProfit(toDecimal(row.getTodayProfit()));
                dto.setHoldingShares(toDecimal(row.getHoldingShares()));
                dto.setCostNav(PortfolioHoldingCostCalculator.inferCostNav(
                        dto.getHoldingAmount(), dto.getHoldingProfit(), dto.getHoldingReturnRate(),
                        dto.getHoldingShares(), dto.getHoldingCost()));
                if (dto.getCostNav() == null) {
                    dto.setCostNav(toDecimal(row.getCostNav()));
                }
                dto.setScreenshotDate(LocalDate.now());
                dto.setConfidence(row.getConfidence());
                dto.setRawTexts(row.getRawTexts() == null
                        ? Collections.emptyList() : row.getRawTexts());

                List<PortfolioHoldingCandidateDto> candidates = rankCandidates(funds, row.getFundName());
                dto.setCandidates(candidates);
                if (!candidates.isEmpty() && candidates.get(0).getScore() != null
                        && candidates.get(0).getScore() >= 80) {
                    dto.setFundCode(candidates.get(0).getFundCode());
                    dto.setFundName(candidates.get(0).getFundName());
                } else {
                    CfgFund exact = exactMap.get(normalizeFundName(row.getFundName()));
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
            throw new BusinessException("未识别到基金持仓，请上传对应账户的基金列表截图");
        }
        if (duplicateCount > 0) {
            warnings.add("已合并滚动截图中的 " + duplicateCount + " 条重复基金");
        }

        FundHoldingImportBatch batch = createBatch(
                ownerUsername, sourceLabel, IMPORT_HOLDING, imageCount, hashes, warnings, ocrResult);
        for (PortfolioHoldingImportRowDto row : rows) {
            itemMapper.insert(toImportItem(batch.getId(), row));
        }

        PortfolioHoldingImportPreviewResponse response = basePreview(batch, hashes, warnings);
        response.setRows(rows);
        response.setTradeAdjustments(Collections.emptyList());
        return response;
    }

    private PortfolioHoldingImportPreviewResponse createTradePreview(
            String ownerUsername, String sourceLabel, List<String> hashes,
            List<String> warnings, PythonOcrResult ocrResult, int imageCount) {
        List<PythonOcrRowResult> recognizedRows = safeOcrImages(ocrResult).stream()
                .flatMap(image -> safeOcrRows(image).stream())
                .collect(Collectors.toList());
        if (recognizedRows.isEmpty()) {
            throw new BusinessException("未识别到基金交易，请上传对应账户的交易明细截图");
        }

        List<UserFundHolding> sourceHoldings = sourceHoldings(ownerUsername, sourceLabel);
        Set<String> appliedFingerprints = appliedTradeFingerprints(ownerUsername, sourceLabel);
        Set<String> seenFingerprints = new HashSet<>();
        FundHoldingImportBatch batch = createBatch(
                ownerUsername, sourceLabel, IMPORT_TRADE, imageCount, hashes, warnings, ocrResult);
        List<FundHoldingTradeImportItem> tradeItems = new ArrayList<>();
        int rowNo = 1;
        for (PythonOcrRowResult row : recognizedRows) {
            FundHoldingTradeImportItem item = new FundHoldingTradeImportItem();
            item.setImportId(batch.getId());
            item.setRowNo(rowNo++);
            item.setFundName(row.getFundName());
            item.setOperationType(normalizeOperation(row.getOperationType()));
            item.setTransactionAmount(toDecimal(row.getTransactionAmount()));
            item.setTransactionAt(toDateTime(row.getTransactionAt()));
            item.setTransactionStatus(normalizeTransactionStatus(row.getTransactionStatus()));
            item.setScreenshotDate(LocalDate.now());
            item.setConfidence(row.getConfidence());
            item.setRawTextJson(writeJson(row.getRawTexts()));

            List<PortfolioHoldingCandidateDto> candidates =
                    rankHoldingCandidates(sourceHoldings, row.getFundName());
            if (!candidates.isEmpty() && candidates.get(0).getScore() != null
                    && candidates.get(0).getScore() >= 80) {
                item.setFundCode(candidates.get(0).getFundCode());
            }
            String groupSeed = hasText(item.getFundCode())
                    ? "code:" + item.getFundCode()
                    : "name:" + normalizeFundName(item.getFundName());
            item.setGroupKey(sha256Text(groupSeed));
            item.setFingerprint(tradeFingerprint(ownerUsername, sourceLabel, item));
            evaluateTradeItem(item, findHolding(sourceHoldings, item.getFundCode()),
                    appliedFingerprints, seenFingerprints);
            tradeItemMapper.insert(item);
            tradeItems.add(item);
        }

        long skipped = tradeItems.stream().filter(this::isSkippedTrade).count();
        if (skipped > 0) {
            warnings.add("共跳过 " + skipped + " 条失败、重复、旧基线或未匹配交易");
            batch.setWarningsJson(writeJson(warnings));
            importMapper.updateById(batch);
        }
        PortfolioHoldingImportPreviewResponse response = basePreview(batch, hashes, warnings);
        response.setRows(Collections.emptyList());
        response.setTradeAdjustments(buildTradeAdjustments(
                ownerUsername, sourceLabel, tradeItems, sourceHoldings));
        return response;
    }

    private FundHoldingImportBatch createBatch(
            String ownerUsername, String sourceLabel, String importType, int imageCount,
            List<String> hashes, List<String> warnings, PythonOcrResult ocrResult) {
        FundHoldingImportBatch batch = new FundHoldingImportBatch();
        batch.setOwnerUsername(ownerUsername);
        batch.setSourceLabel(sourceLabel);
        batch.setImportType(importType);
        batch.setStatus("PREVIEWED");
        batch.setScreenshotDate(LocalDate.now());
        batch.setImageCount(imageCount);
        batch.setImageHashesJson(writeJson(hashes));
        batch.setRawOcrJson(writeJson(ocrResult));
        batch.setWarningsJson(writeJson(warnings));
        batch.setParserVersion(PARSER_VERSION);
        importMapper.insert(batch);
        return batch;
    }

    private PortfolioHoldingImportPreviewResponse basePreview(
            FundHoldingImportBatch batch, List<String> hashes, List<String> warnings) {
        PortfolioHoldingImportPreviewResponse response = new PortfolioHoldingImportPreviewResponse();
        response.setImportId(batch.getId());
        response.setSourceLabel(batch.getSourceLabel());
        response.setImportType(normalizeImportType(batch.getImportType()));
        response.setStatus(batch.getStatus());
        response.setScreenshotDate(batch.getScreenshotDate());
        response.setImageCount(batch.getImageCount());
        response.setImageHashes(hashes);
        response.setWarnings(warnings);
        return response;
    }

    @Transactional
    @Override
    public PortfolioHoldingConfirmResponse confirm(String ownerUsername, Long importId,
                                                   PortfolioHoldingConfirmRequest request) {
        FundHoldingImportBatch batch = loadBatch(ownerUsername, importId);
        if (!"PREVIEWED".equalsIgnoreCase(batch.getStatus())
                && !"CONFIRMED".equalsIgnoreCase(batch.getStatus())) {
            throw new BusinessException("导入批次状态不可确认");
        }
        if (IMPORT_TRADE.equals(normalizeImportType(batch.getImportType()))) {
            return confirmTradeImport(ownerUsername, batch, request);
        }
        return confirmHoldingImport(ownerUsername, batch, request);
    }

    private PortfolioHoldingConfirmResponse confirmHoldingImport(
            String ownerUsername, FundHoldingImportBatch batch,
            PortfolioHoldingConfirmRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("请至少保留一条持仓");
        }

        LocalDate screenshotDate = request.getScreenshotDate() != null
                ? request.getScreenshotDate() : batch.getScreenshotDate();
        String sourceLabel = normalizeSource(batch.getSourceLabel());
        List<PortfolioHoldingConfirmItemRequest> items = request.getItems();
        List<FundHoldingImportItem> importItems = new ArrayList<>();
        Set<String> confirmedFundCodes = new HashSet<>();
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
            holding.setSourceLabel(sourceLabel);
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
            holding.setLatestImportId(batch.getId());
            holding.setLatestImportAt(LocalDateTime.now());
            upsertHolding(holding);
            confirmedFundCodes.add(fund.getFundCode());

            FundHoldingImportItem item = new FundHoldingImportItem();
            item.setImportId(batch.getId());
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

        itemMapper.delete(new LambdaQueryWrapper<FundHoldingImportItem>()
                .eq(FundHoldingImportItem::getImportId, batch.getId()));
        for (FundHoldingImportItem item : importItems) {
            itemMapper.insert(item);
        }
        holdingMapper.delete(new LambdaQueryWrapper<UserFundHolding>()
                .eq(UserFundHolding::getOwnerUsername, ownerUsername)
                .eq(UserFundHolding::getSourceLabel, sourceLabel)
                .notIn(UserFundHolding::getFundCode, confirmedFundCodes));

        batch.setStatus("CONFIRMED");
        batch.setConfirmedAt(LocalDateTime.now());
        batch.setScreenshotDate(screenshotDate);
        importMapper.updateById(batch);
        PortfolioHoldingConfirmResponse response = new PortfolioHoldingConfirmResponse();
        response.setAffectedHoldingCount(confirmedFundCodes.size());
        response.setAppliedTransactionCount(0);
        response.setSkippedTransactionCount(0);
        response.setWarnings(Collections.emptyList());
        return response;
    }

    private PortfolioHoldingConfirmResponse confirmTradeImport(
            String ownerUsername, FundHoldingImportBatch batch,
            PortfolioHoldingConfirmRequest request) {
        List<FundHoldingTradeImportItem> allItems = tradeItemMapper.selectList(
                new LambdaQueryWrapper<FundHoldingTradeImportItem>()
                        .eq(FundHoldingTradeImportItem::getImportId, batch.getId())
                        .orderByAsc(FundHoldingTradeImportItem::getRowNo));
        if (allItems.isEmpty()) {
            throw new BusinessException("交易导入批次没有可确认的明细");
        }
        if ("CONFIRMED".equalsIgnoreCase(batch.getStatus())) {
            return tradeConfirmResult(allItems, Collections.singletonList("该批次已确认，未重复调整"));
        }

        Map<String, String> mappingByGroup = request == null || request.getTradeMappings() == null
                ? Collections.emptyMap()
                : request.getTradeMappings().stream()
                .filter(mapping -> hasText(mapping.getGroupKey()) && hasText(mapping.getFundCode()))
                .collect(Collectors.toMap(
                        PortfolioTradeMappingRequest::getGroupKey,
                        PortfolioTradeMappingRequest::getFundCode,
                        (left, right) -> right,
                        LinkedHashMap::new));
        Map<String, List<FundHoldingTradeImportItem>> groups = allItems.stream()
                .collect(Collectors.groupingBy(
                        FundHoldingTradeImportItem::getGroupKey,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<String> warnings = new ArrayList<>();
        int affectedHoldingCount = 0;
        int appliedTransactionCount = 0;

        for (Map.Entry<String, List<FundHoldingTradeImportItem>> entry : groups.entrySet()) {
            List<FundHoldingTradeImportItem> group = entry.getValue();
            String mappedCode = mappingByGroup.get(entry.getKey());
            if (!hasText(mappedCode)) {
                mappedCode = group.stream()
                        .map(FundHoldingTradeImportItem::getFundCode)
                        .filter(this::hasText)
                        .findFirst()
                        .orElse(null);
            }
            UserFundHolding holding = loadSourceHolding(
                    ownerUsername, batch.getSourceLabel(), mappedCode);
            if (holding == null) {
                for (FundHoldingTradeImportItem item : group) {
                    if (!isPermanentTradeSkip(item)) {
                        markSkipped(item, "SKIPPED_MISSING_HOLDING", "所选平台没有对应基金持仓");
                        tradeItemMapper.updateById(item);
                    }
                }
                warnings.add(displayFundName(group) + "：所选平台没有对应持仓，未调整");
                continue;
            }

            List<FundHoldingTradeImportItem> applicable = new ArrayList<>();
            for (FundHoldingTradeImportItem item : group) {
                item.setFundCode(holding.getFundCode());
                if ("FAILED".equals(normalizeTransactionStatus(item.getTransactionStatus()))) {
                    markSkipped(item, "SKIPPED_FAILED", "交易状态为失败、关闭或撤销");
                } else if (item.getTransactionAt() == null || item.getTransactionAmount() == null
                        || item.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0
                        || !hasText(item.getOperationType())) {
                    markSkipped(item, "SKIPPED_INVALID", "交易方向、金额或时间不完整");
                } else if (holding.getScreenshotDate() != null
                        && !item.getTransactionAt().toLocalDate().isAfter(holding.getScreenshotDate())) {
                    markSkipped(item, "SKIPPED_BASELINE", "交易日期早于或等于最近持仓快照");
                } else if (alreadyApplied(item.getFingerprint(), item.getId())) {
                    markSkipped(item, "SKIPPED_DUPLICATE", "交易已在其他批次应用");
                } else {
                    item.setStatus("APPLICABLE");
                    item.setSkipReason(null);
                    applicable.add(item);
                }
            }
            if (applicable.isEmpty()) {
                for (FundHoldingTradeImportItem item : group) {
                    tradeItemMapper.updateById(item);
                }
                continue;
            }

            BigDecimal buyAmount = sumTradeAmount(applicable, "BUY");
            BigDecimal sellAmount = sumTradeAmount(applicable, "SELL");
            BigDecimal before = zeroIfNull(holding.getHoldingAmount());
            BigDecimal requestedAfter = before.add(buyAmount).subtract(sellAmount);
            BigDecimal after = requestedAfter.max(BigDecimal.ZERO);
            boolean clamped = requestedAfter.compareTo(BigDecimal.ZERO) < 0;

            holding.setHoldingAmount(after);
            holding.setLatestImportId(batch.getId());
            holding.setLatestImportAt(LocalDateTime.now());
            holdingMapper.updateById(holding);
            affectedHoldingCount++;

            for (FundHoldingTradeImportItem item : group) {
                item.setFundCode(holding.getFundCode());
                item.setBeforeHoldingAmount(before);
                item.setAfterHoldingAmount(after);
                if (applicable.contains(item)) {
                    item.setAppliedKey(item.getFingerprint());
                    item.setStatus(clamped ? "APPLIED_CLAMPED" : "APPLIED");
                    item.setSkipReason(clamped ? "卖出金额超过当前持仓，已按0封顶" : null);
                    appliedTransactionCount++;
                }
                tradeItemMapper.updateById(item);
            }
            if (clamped) {
                warnings.add(holding.getFundName() + "：卖出后金额已按0封顶，持仓行保留");
            }
        }

        batch.setStatus("CONFIRMED");
        batch.setConfirmedAt(LocalDateTime.now());
        if (!warnings.isEmpty()) {
            List<String> allWarnings = new ArrayList<>(parseJsonList(batch.getWarningsJson()));
            allWarnings.addAll(warnings);
            batch.setWarningsJson(writeJson(allWarnings));
        }
        importMapper.updateById(batch);
        PortfolioHoldingConfirmResponse response = tradeConfirmResult(allItems, warnings);
        response.setAffectedHoldingCount(affectedHoldingCount);
        response.setAppliedTransactionCount(appliedTransactionCount);
        response.setSkippedTransactionCount(allItems.size() - appliedTransactionCount);
        return response;
    }

    @Override
    public Page<UserFundHoldingDto> holdings(String ownerUsername, long current, long size, String keyword,
                                             String scope, String sortField, String sortOrder) {
        String normalizedScope = normalizeScope(scope);
        List<UserFundHoldingDto> records = holdingMapper.selectList(new LambdaQueryWrapper<UserFundHolding>()
                .eq(UserFundHolding::getOwnerUsername, ownerUsername)
                .like(hasText(keyword), UserFundHolding::getFundName, keyword)
                .eq("alipay".equals(normalizedScope) || "tencent".equals(normalizedScope),
                        UserFundHolding::getSourceLabel, normalizedScope))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        if ("all".equals(normalizedScope)) {
            records = aggregateHoldings(records);
        }
        records.sort(holdingComparator(sortField, sortOrder));
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.max(1, Math.min(200, size));
        int from = (int) Math.min(records.size(), (safeCurrent - 1) * safeSize);
        int to = (int) Math.min(records.size(), from + safeSize);
        Page<UserFundHoldingDto> result = new Page<>(safeCurrent, safeSize, records.size());
        result.setRecords(new ArrayList<>(records.subList(from, to)));
        return result;
    }

    @Override
    public PortfolioOverviewDto overview(String ownerUsername) {
        List<UserFundHoldingDto> holdings = holdingMapper.selectList(new LambdaQueryWrapper<UserFundHolding>()
                        .eq(UserFundHolding::getOwnerUsername, ownerUsername))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        PortfolioOverviewDto overview = new PortfolioOverviewDto();
        overview.setTotal(toSummary("all", "全部账户", holdings));
        List<PortfolioAccountSummaryDto> accounts = new ArrayList<>();
        accounts.add(toSummary("alipay", "支付宝", holdings.stream()
                .filter(item -> "alipay".equals(item.getSourceLabel())).collect(Collectors.toList())));
        accounts.add(toSummary("tencent", "腾讯理财通", holdings.stream()
                .filter(item -> "tencent".equals(item.getSourceLabel())).collect(Collectors.toList())));
        overview.setAccounts(accounts);
        return overview;
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
        List<FundHoldingTradeImportItem> tradeItems = batchIds.isEmpty()
                ? Collections.emptyList()
                : tradeItemMapper.selectList(new LambdaQueryWrapper<FundHoldingTradeImportItem>()
                .in(FundHoldingTradeImportItem::getImportId, batchIds));
        Map<Long, List<FundHoldingTradeImportItem>> tradeItemsByBatch = tradeItems.stream()
                .collect(Collectors.groupingBy(FundHoldingTradeImportItem::getImportId));
        result.setRecords(page.getRecords().stream().map(batch -> {
            PortfolioHoldingBatchSummaryDto dto = new PortfolioHoldingBatchSummaryDto();
            dto.setId(batch.getId());
            dto.setStatus(batch.getStatus());
            dto.setSourceLabel(batch.getSourceLabel());
            String importType = normalizeImportType(batch.getImportType());
            dto.setImportType(importType);
            dto.setScreenshotDate(batch.getScreenshotDate());
            dto.setImageCount(batch.getImageCount());
            List<FundHoldingTradeImportItem> batchTradeItems =
                    tradeItemsByBatch.getOrDefault(batch.getId(), Collections.emptyList());
            if (IMPORT_TRADE.equals(importType)) {
                dto.setItemCount((int) batchTradeItems.stream()
                        .map(FundHoldingTradeImportItem::getGroupKey).distinct().count());
                dto.setTransactionCount(batchTradeItems.size());
                dto.setAppliedCount((int) batchTradeItems.stream()
                        .filter(this::isAppliedTrade).count());
                dto.setSkippedCount((int) batchTradeItems.stream()
                        .filter(this::isSkippedTrade).count());
            } else {
                dto.setItemCount(itemCounts.getOrDefault(batch.getId(), 0L).intValue());
                dto.setTransactionCount(0);
                dto.setAppliedCount(0);
                dto.setSkippedCount(0);
            }
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
        String importType = normalizeImportType(batch.getImportType());
        if (IMPORT_TRADE.equals(importType)) {
            List<FundHoldingTradeImportItem> tradeItems = tradeItemMapper.selectList(
                    new LambdaQueryWrapper<FundHoldingTradeImportItem>()
                            .eq(FundHoldingTradeImportItem::getImportId, importId)
                            .orderByAsc(FundHoldingTradeImportItem::getRowNo));
            PortfolioHoldingImportPreviewResponse response = basePreview(
                    batch, parseJsonList(batch.getImageHashesJson()),
                    parseJsonList(batch.getWarningsJson()));
            response.setRows(Collections.emptyList());
            response.setTradeAdjustments(buildTradeAdjustments(
                    ownerUsername, batch.getSourceLabel(), tradeItems,
                    sourceHoldings(ownerUsername, batch.getSourceLabel())));
            return response;
        }
        List<FundHoldingImportItem> items = itemMapper.selectList(new LambdaQueryWrapper<FundHoldingImportItem>()
                .eq(FundHoldingImportItem::getImportId, importId)
                .orderByAsc(FundHoldingImportItem::getRowNo));
        PortfolioHoldingImportPreviewResponse response = new PortfolioHoldingImportPreviewResponse();
        response.setImportId(batch.getId());
        response.setSourceLabel(batch.getSourceLabel());
        response.setImportType(importType);
        response.setStatus(batch.getStatus());
        response.setScreenshotDate(batch.getScreenshotDate());
        response.setImageCount(batch.getImageCount());
        response.setImageHashes(parseJsonList(batch.getImageHashesJson()));
        response.setWarnings(parseJsonList(batch.getWarningsJson()));
        response.setRows(items.stream().map(this::toPreviewRow).collect(Collectors.toList()));
        response.setTradeAdjustments(Collections.emptyList());
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
        dto.setSourceLabel(hasText(holding.getSourceLabel()) ? holding.getSourceLabel() : "alipay");
        dto.setFundCode(holding.getFundCode());
        dto.setFundName(holding.getFundName());
        CfgFund fund = fundMapper.selectOne(new LambdaQueryWrapper<CfgFund>()
                .select(CfgFund::getFundType)
                .eq(CfgFund::getFundCode, holding.getFundCode())
                .last("limit 1"));
        dto.setFundType(fund == null ? null : fund.getFundType());
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
            dto.setHoldingCutoffDate(valuation.getHoldingCutoffDate());
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

    private List<PortfolioHoldingCandidateDto> rankHoldingCandidates(
            List<UserFundHolding> holdings, String fundName) {
        if (!hasText(fundName)) {
            return Collections.emptyList();
        }
        String normalized = normalizeFundName(fundName);
        List<PortfolioHoldingCandidateDto> candidates = new ArrayList<>();
        for (UserFundHolding holding : holdings) {
            if (!hasText(holding.getFundName()) || !hasText(holding.getFundCode())) {
                continue;
            }
            int score = scoreCandidate(normalized, normalizeFundName(holding.getFundName()));
            if (score < 0) {
                continue;
            }
            PortfolioHoldingCandidateDto candidate = new PortfolioHoldingCandidateDto();
            candidate.setFundCode(holding.getFundCode());
            candidate.setFundName(holding.getFundName());
            candidate.setScore(score);
            candidates.add(candidate);
        }
        candidates.sort((left, right) -> Integer.compare(right.getScore(), left.getScore()));
        return candidates.stream().limit(5).collect(Collectors.toList());
    }

    private List<PortfolioTradeAdjustmentDto> buildTradeAdjustments(
            String ownerUsername, String sourceLabel,
            List<FundHoldingTradeImportItem> items, List<UserFundHolding> holdings) {
        Map<String, List<FundHoldingTradeImportItem>> groups = items.stream()
                .collect(Collectors.groupingBy(
                        FundHoldingTradeImportItem::getGroupKey,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<PortfolioTradeAdjustmentDto> result = new ArrayList<>();
        for (Map.Entry<String, List<FundHoldingTradeImportItem>> entry : groups.entrySet()) {
            List<FundHoldingTradeImportItem> group = entry.getValue();
            FundHoldingTradeImportItem first = group.get(0);
            PortfolioTradeAdjustmentDto dto = new PortfolioTradeAdjustmentDto();
            dto.setGroupKey(entry.getKey());
            dto.setFundCode(group.stream()
                    .map(FundHoldingTradeImportItem::getFundCode)
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(null));
            UserFundHolding holding = findHolding(holdings, dto.getFundCode());
            dto.setFundName(holding != null ? holding.getFundName() : first.getFundName());
            dto.setCandidates(rankHoldingCandidates(holdings, first.getFundName()));

            List<FundHoldingTradeImportItem> included = group.stream()
                    .filter(item -> "APPLICABLE".equals(item.getStatus())
                            || isAppliedTrade(item))
                    .collect(Collectors.toList());
            BigDecimal buyAmount = sumTradeAmount(included, "BUY");
            BigDecimal sellAmount = sumTradeAmount(included, "SELL");
            BigDecimal netAmount = buyAmount.subtract(sellAmount);
            dto.setBuyAmount(buyAmount);
            dto.setSellAmount(sellAmount);
            dto.setNetAmount(netAmount);
            dto.setTransactionCount(group.size());
            dto.setSkippedCount((int) group.stream().filter(this::isSkippedTrade).count());

            BigDecimal storedBefore = group.stream()
                    .map(FundHoldingTradeImportItem::getBeforeHoldingAmount)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            BigDecimal storedAfter = group.stream()
                    .map(FundHoldingTradeImportItem::getAfterHoldingAmount)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            BigDecimal current = storedBefore != null
                    ? storedBefore
                    : holding == null ? null : zeroIfNull(holding.getHoldingAmount());
            dto.setCurrentHoldingAmount(current);
            dto.setProjectedHoldingAmount(storedAfter != null
                    ? storedAfter
                    : current == null ? null : current.add(netAmount).max(BigDecimal.ZERO));
            dto.setApplicable(holding != null && !included.isEmpty());
            dto.setWarnings(group.stream()
                    .map(FundHoldingTradeImportItem::getSkipReason)
                    .filter(this::hasText)
                    .distinct()
                    .collect(Collectors.toList()));
            result.add(dto);
        }
        return result;
    }

    private void evaluateTradeItem(
            FundHoldingTradeImportItem item, UserFundHolding holding,
            Set<String> appliedFingerprints, Set<String> seenFingerprints) {
        if (!hasText(item.getOperationType()) || item.getTransactionAmount() == null
                || item.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0
                || item.getTransactionAt() == null || !hasText(item.getFundName())) {
            markSkipped(item, "SKIPPED_INVALID", "交易方向、基金、金额或时间不完整");
        } else if ("FAILED".equals(item.getTransactionStatus())) {
            markSkipped(item, "SKIPPED_FAILED", "交易状态为失败、关闭或撤销");
        } else if (appliedFingerprints.contains(item.getFingerprint())
                || !seenFingerprints.add(item.getFingerprint())) {
            markSkipped(item, "SKIPPED_DUPLICATE", "交易已经应用或在本批次重复");
        } else if (holding == null) {
            markSkipped(item, "SKIPPED_UNMATCHED", "未匹配到所选平台的现有持仓");
        } else if (holding.getScreenshotDate() != null
                && !item.getTransactionAt().toLocalDate().isAfter(holding.getScreenshotDate())) {
            markSkipped(item, "SKIPPED_BASELINE", "交易日期早于或等于最近持仓快照");
        } else {
            item.setStatus("APPLICABLE");
            item.setSkipReason(null);
        }
    }

    private Set<String> confirmedTradeImageHashes(String ownerUsername, String sourceLabel) {
        List<FundHoldingImportBatch> batches = importMapper.selectList(
                new LambdaQueryWrapper<FundHoldingImportBatch>()
                        .select(FundHoldingImportBatch::getImageHashesJson)
                        .eq(FundHoldingImportBatch::getOwnerUsername, ownerUsername)
                        .eq(FundHoldingImportBatch::getSourceLabel, sourceLabel)
                        .eq(FundHoldingImportBatch::getImportType, IMPORT_TRADE)
                        .eq(FundHoldingImportBatch::getStatus, "CONFIRMED"));
        Set<String> result = new HashSet<>();
        for (FundHoldingImportBatch batch : batches) {
            result.addAll(parseJsonList(batch.getImageHashesJson()));
        }
        return result;
    }

    private Set<String> appliedTradeFingerprints(String ownerUsername, String sourceLabel) {
        List<Long> batchIds = importMapper.selectList(
                        new LambdaQueryWrapper<FundHoldingImportBatch>()
                                .select(FundHoldingImportBatch::getId)
                                .eq(FundHoldingImportBatch::getOwnerUsername, ownerUsername)
                                .eq(FundHoldingImportBatch::getSourceLabel, sourceLabel)
                                .eq(FundHoldingImportBatch::getImportType, IMPORT_TRADE))
                .stream()
                .map(FundHoldingImportBatch::getId)
                .collect(Collectors.toList());
        if (batchIds.isEmpty()) {
            return Collections.emptySet();
        }
        return tradeItemMapper.selectList(new LambdaQueryWrapper<FundHoldingTradeImportItem>()
                        .select(FundHoldingTradeImportItem::getAppliedKey)
                        .in(FundHoldingTradeImportItem::getImportId, batchIds)
                        .isNotNull(FundHoldingTradeImportItem::getAppliedKey))
                .stream()
                .map(FundHoldingTradeImportItem::getAppliedKey)
                .filter(this::hasText)
                .collect(Collectors.toSet());
    }

    private boolean alreadyApplied(String fingerprint, Long currentItemId) {
        if (!hasText(fingerprint)) {
            return false;
        }
        return tradeItemMapper.selectCount(new LambdaQueryWrapper<FundHoldingTradeImportItem>()
                .eq(FundHoldingTradeImportItem::getAppliedKey, fingerprint)
                .ne(currentItemId != null, FundHoldingTradeImportItem::getId, currentItemId)) > 0;
    }

    private List<UserFundHolding> sourceHoldings(String ownerUsername, String sourceLabel) {
        return holdingMapper.selectList(new LambdaQueryWrapper<UserFundHolding>()
                .eq(UserFundHolding::getOwnerUsername, ownerUsername)
                .eq(UserFundHolding::getSourceLabel, normalizeSource(sourceLabel)));
    }

    private UserFundHolding loadSourceHolding(
            String ownerUsername, String sourceLabel, String fundCode) {
        if (!hasText(fundCode)) {
            return null;
        }
        return holdingMapper.selectOne(new LambdaQueryWrapper<UserFundHolding>()
                .eq(UserFundHolding::getOwnerUsername, ownerUsername)
                .eq(UserFundHolding::getSourceLabel, normalizeSource(sourceLabel))
                .eq(UserFundHolding::getFundCode, fundCode)
                .last("limit 1"));
    }

    private UserFundHolding findHolding(List<UserFundHolding> holdings, String fundCode) {
        if (!hasText(fundCode)) {
            return null;
        }
        return holdings.stream()
                .filter(holding -> fundCode.equals(holding.getFundCode()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal sumTradeAmount(
            List<FundHoldingTradeImportItem> items, String operationType) {
        return items.stream()
                .filter(item -> operationType.equals(item.getOperationType()))
                .map(FundHoldingTradeImportItem::getTransactionAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PortfolioHoldingConfirmResponse tradeConfirmResult(
            List<FundHoldingTradeImportItem> items, List<String> warnings) {
        PortfolioHoldingConfirmResponse response = new PortfolioHoldingConfirmResponse();
        response.setAffectedHoldingCount((int) items.stream()
                .filter(this::isAppliedTrade)
                .map(FundHoldingTradeImportItem::getFundCode)
                .filter(this::hasText)
                .distinct()
                .count());
        response.setAppliedTransactionCount((int) items.stream()
                .filter(this::isAppliedTrade).count());
        response.setSkippedTransactionCount((int) items.stream()
                .filter(this::isSkippedTrade).count());
        response.setWarnings(warnings == null ? Collections.emptyList() : warnings);
        return response;
    }

    private boolean isAppliedTrade(FundHoldingTradeImportItem item) {
        return item != null && hasText(item.getStatus())
                && item.getStatus().startsWith("APPLIED");
    }

    private boolean isSkippedTrade(FundHoldingTradeImportItem item) {
        return item != null && hasText(item.getStatus())
                && item.getStatus().startsWith("SKIPPED");
    }

    private boolean isPermanentTradeSkip(FundHoldingTradeImportItem item) {
        if (item == null || !hasText(item.getStatus())) {
            return false;
        }
        return "SKIPPED_FAILED".equals(item.getStatus())
                || "SKIPPED_INVALID".equals(item.getStatus())
                || "SKIPPED_DUPLICATE".equals(item.getStatus())
                || "SKIPPED_BASELINE".equals(item.getStatus());
    }

    private void markSkipped(
            FundHoldingTradeImportItem item, String status, String reason) {
        item.setStatus(status);
        item.setSkipReason(reason);
        item.setAppliedKey(null);
    }

    private String displayFundName(List<FundHoldingTradeImportItem> items) {
        return items.stream()
                .map(FundHoldingTradeImportItem::getFundName)
                .filter(this::hasText)
                .findFirst()
                .orElse("未识别基金");
    }

    private String tradeFingerprint(
            String ownerUsername, String sourceLabel, FundHoldingTradeImportItem item) {
        String amount = item.getTransactionAmount() == null
                ? "" : item.getTransactionAmount().stripTrailingZeros().toPlainString();
        String timestamp = item.getTransactionAt() == null
                ? "" : item.getTransactionAt().toString();
        return sha256Text(ownerUsername + "|" + sourceLabel + "|"
                + normalizeFundName(item.getFundName()) + "|"
                + (item.getOperationType() == null ? "" : item.getOperationType()) + "|"
                + amount + "|" + timestamp);
    }

    private String sha256Text(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new BusinessException("生成交易指纹失败");
        }
    }

    private LocalDateTime toDateTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String normalizeOperation(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "BUY".equals(normalized) || "SELL".equals(normalized) ? normalized : null;
    }

    private String normalizeTransactionStatus(String value) {
        if (!hasText(value)) {
            return "UNKNOWN";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("FAILED".equals(normalized)) {
            return "FAILED";
        }
        return "SUCCESS".equals(normalized) ? "SUCCESS" : "UNKNOWN";
    }

    private List<PythonOcrImageResult> safeOcrImages(PythonOcrResult result) {
        return result == null || result.getImages() == null
                ? Collections.emptyList() : result.getImages();
    }

    private List<PythonOcrRowResult> safeOcrRows(PythonOcrImageResult image) {
        return image == null || image.getRows() == null
                ? Collections.emptyList() : image.getRows();
    }

    private String safeFilename(String filename) {
        return hasText(filename) ? filename : "未命名截图";
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

    private String normalizeSource(String sourceLabel) {
        String normalized = hasText(sourceLabel) ? sourceLabel.trim().toLowerCase(Locale.ROOT) : "alipay";
        if (!"alipay".equals(normalized) && !"tencent".equals(normalized)) {
            throw new BusinessException("不支持的持仓来源");
        }
        return normalized;
    }

    private String normalizeImportType(String importType) {
        String normalized = hasText(importType)
                ? importType.trim().toLowerCase(Locale.ROOT) : IMPORT_HOLDING;
        if (!IMPORT_HOLDING.equals(normalized) && !IMPORT_TRADE.equals(normalized)) {
            throw new BusinessException("不支持的导入类型");
        }
        return normalized;
    }

    private String normalizeScope(String scope) {
        String normalized = hasText(scope) ? scope.trim().toLowerCase(Locale.ROOT) : "raw";
        if (!"raw".equals(normalized) && !"all".equals(normalized)
                && !"alipay".equals(normalized) && !"tencent".equals(normalized)) {
            throw new BusinessException("不支持的持仓范围");
        }
        return normalized;
    }

    private List<UserFundHoldingDto> aggregateHoldings(List<UserFundHoldingDto> holdings) {
        Map<String, UserFundHoldingDto> grouped = new LinkedHashMap<>();
        for (UserFundHoldingDto source : holdings) {
            UserFundHoldingDto target = grouped.get(source.getFundCode());
            if (target == null) {
                target = new UserFundHoldingDto();
                target.setId(source.getId());
                target.setOwnerUsername(source.getOwnerUsername());
                target.setSourceLabel("all");
                target.setFundCode(source.getFundCode());
                target.setFundName(source.getFundName());
                target.setFundType(source.getFundType());
                target.setScreenshotDate(source.getScreenshotDate());
                target.setLatestImportAt(source.getLatestImportAt());
                grouped.put(source.getFundCode(), target);
            }
            target.setHoldingAmount(add(target.getHoldingAmount(), source.getHoldingAmount()));
            target.setHoldingProfit(add(target.getHoldingProfit(), source.getHoldingProfit()));
            target.setHoldingCost(add(target.getHoldingCost(), source.getHoldingCost()));
            target.setYesterdayProfit(add(target.getYesterdayProfit(), source.getYesterdayProfit()));
            target.setTodayProfit(add(target.getTodayProfit(), source.getTodayProfit()));
            target.setHoldingShares(add(target.getHoldingShares(), source.getHoldingShares()));
            target.setEstimatedDailyProfit(add(target.getEstimatedDailyProfit(), source.getEstimatedDailyProfit()));
            target.setEstimatedHoldingAmount(add(target.getEstimatedHoldingAmount(), source.getEstimatedHoldingAmount()));
            target.setEstimatedCumulativeProfit(add(
                    target.getEstimatedCumulativeProfit(), source.getEstimatedCumulativeProfit()));
            if (source.getLatestImportAt() != null && (target.getLatestImportAt() == null
                    || source.getLatestImportAt().isAfter(target.getLatestImportAt()))) {
                target.setLatestImportAt(source.getLatestImportAt());
                target.setScreenshotDate(source.getScreenshotDate());
            }
        }
        for (UserFundHoldingDto item : grouped.values()) {
            item.setHoldingReturnRate(rate(item.getHoldingProfit(), item.getHoldingCost()));
        }
        return new ArrayList<>(grouped.values());
    }

    private PortfolioAccountSummaryDto toSummary(String sourceLabel, String displayName,
                                                  List<UserFundHoldingDto> holdings) {
        PortfolioAccountSummaryDto summary = new PortfolioAccountSummaryDto();
        summary.setSourceLabel(sourceLabel);
        summary.setDisplayName(displayName);
        summary.setHoldingCount((int) holdings.stream().map(UserFundHoldingDto::getFundCode).distinct().count());
        BigDecimal amount = null;
        BigDecimal profit = null;
        BigDecimal cost = null;
        BigDecimal today = null;
        for (UserFundHoldingDto item : holdings) {
            amount = add(amount, item.getHoldingAmount());
            profit = add(profit, item.getHoldingProfit());
            cost = add(cost, item.getHoldingCost());
            today = add(today, item.getEstimatedDailyProfit() != null
                    ? item.getEstimatedDailyProfit() : item.getTodayProfit());
        }
        summary.setHoldingAmount(zeroIfNull(amount));
        summary.setHoldingProfit(zeroIfNull(profit));
        summary.setHoldingReturnRate(rate(profit, cost));
        summary.setTodayProfit(zeroIfNull(today));
        return summary;
    }

    private Comparator<UserFundHoldingDto> holdingComparator(String sortField, String sortOrder) {
        String field = hasText(sortField) ? sortField : "holdingAmount";
        Comparator<UserFundHoldingDto> comparator;
        switch (field) {
            case "fundName":
                comparator = Comparator.comparing(
                        item -> item.getFundName() == null ? "" : item.getFundName(),
                        Comparator.naturalOrder());
                break;
            case "estimatedDailyProfit":
                comparator = Comparator.comparing(
                        item -> item.getEstimatedDailyProfit() != null
                                ? item.getEstimatedDailyProfit() : item.getTodayProfit(),
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "fundType":
                comparator = Comparator.comparing(
                        item -> item.getFundType() == null ? "" : item.getFundType(),
                        Comparator.naturalOrder());
                break;
            case "holdingProfit":
                comparator = decimalComparator(UserFundHoldingDto::getHoldingProfit);
                break;
            case "holdingReturnRate":
                comparator = decimalComparator(UserFundHoldingDto::getHoldingReturnRate);
                break;
            case "holdingAmount":
            default:
                comparator = decimalComparator(UserFundHoldingDto::getHoldingAmount);
                break;
        }
        if (!"asc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(item -> item.getFundCode() == null ? "" : item.getFundCode());
    }

    private Comparator<UserFundHoldingDto> decimalComparator(
            java.util.function.Function<UserFundHoldingDto, BigDecimal> getter) {
        return Comparator.comparing(getter, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        if (right == null) {
            return left;
        }
        return left == null ? right : left.add(right);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal rate(BigDecimal profit, BigDecimal cost) {
        if (profit == null || cost == null || cost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return profit.multiply(new BigDecimal("100")).divide(cost, 4, RoundingMode.HALF_UP);
    }

    private void upsertHolding(UserFundHolding holding) {
        UserFundHolding existing = holdingMapper.selectOne(new LambdaQueryWrapper<UserFundHolding>()
                .eq(UserFundHolding::getOwnerUsername, holding.getOwnerUsername())
                .eq(UserFundHolding::getSourceLabel, holding.getSourceLabel())
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

        private PythonOcrResult recognize(
                List<File> images, String sourceLabel, String importType) throws IOException {
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(resolveScriptPath().toString());
            command.add("--json");
            command.add("--source-label");
            command.add(sourceLabel);
            command.add("--import-type");
            command.add(importType);
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
        private String operationType;
        private String transactionAmount;
        private String transactionAt;
        private String transactionStatus;
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

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public String getTransactionAmount() {
            return transactionAmount;
        }

        public void setTransactionAmount(String transactionAmount) {
            this.transactionAmount = transactionAmount;
        }

        public String getTransactionAt() {
            return transactionAt;
        }

        public void setTransactionAt(String transactionAt) {
            this.transactionAt = transactionAt;
        }

        public String getTransactionStatus() {
            return transactionStatus;
        }

        public void setTransactionStatus(String transactionStatus) {
            this.transactionStatus = transactionStatus;
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
