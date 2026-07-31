package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.example.crm.dto.FundDailyValuationDto;
import com.example.crm.dto.score.FundScoreSummaryDto;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_detail")
public class CfgFund {
    private Long id;
    private String fundCode;
    private String fundName;
    private LocalDate inceptionDate;
    private String fundManager;
    private String fundType;
    private String managementCompany;
    private String netAssetScale;
    private LocalDate scaleDate;
    private Boolean canBuy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField(exist = false)
    private FundPerformanceHistory latestPerformance;
    @TableField(exist = false)
    private FundRating latestRating;
    @TableField(exist = false)
    private java.util.List<FundFeatureData> features;
    @TableField(exist = false)
    private FundDailyValuationDto latestValuation;
    @TableField(exist = false)
    private FundScoreSummaryDto latestScore;
    @TableField(exist = false)
    private Boolean favorite;
}
