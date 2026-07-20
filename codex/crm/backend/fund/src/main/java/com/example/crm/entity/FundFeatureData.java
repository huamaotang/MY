package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fund_feature_data")
public class FundFeatureData {
    private Long id;
    private String fundCode;
    private String periodLabel;
    private String cutoffDate;
    private BigDecimal standardDeviation;
    private BigDecimal sharpeRatio;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
