package com.example.crm.dto;

import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundFeatureData;
import com.example.crm.entity.FundNavHistory;
import com.example.crm.entity.FundPerformanceHistory;
import com.example.crm.entity.FundRating;
import com.example.crm.entity.FundStockHolding;
import lombok.Data;

import java.util.List;

@Data
public class FundDetailResponse {
    private CfgFund fund;
    private FundNavHistory latestNav;
    private FundPerformanceHistory latestPerformance;
    private FundDailyValuationDto latestValuation;
    private List<FundStockHolding> latestHoldings;
    private List<FundFeatureData> features;
    private List<FundRating> ratings;
}
