package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fund_nav_history")
public class FundNavHistory {
    private Long id;
    private String fundCode;
    private String navDate;
    private BigDecimal unitNav;
    private BigDecimal accumulatedNav;
    private BigDecimal dailyGrowthRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
