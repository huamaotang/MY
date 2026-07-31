package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("fund_holding_import")
public class FundHoldingImportBatch {
    private Long id;
    private String ownerUsername;
    private String sourceLabel;
    private String importType;
    private String status;
    private LocalDate screenshotDate;
    private Integer imageCount;
    private String imageHashesJson;
    private String rawOcrJson;
    private String warningsJson;
    private String parserVersion;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<FundHoldingImportItem> items;
}
