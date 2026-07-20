package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fund_stock_holding")
public class FundStockHolding {
    private Long id;
    private String fundCode;
    private String reportPeriod;
    private String reportDate;
    private Integer rankNo;
    private String stockCode;
    private String stockName;
    private BigDecimal latestPrice;
    private BigDecimal changeRate;
    private String relatedInfoUrl;
    private BigDecimal netValueRatio;
    @TableField("holding_shares_10k")
    private BigDecimal holdingShares10k;
    @TableField("holding_market_value_10k")
    private BigDecimal holdingMarketValue10k;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
