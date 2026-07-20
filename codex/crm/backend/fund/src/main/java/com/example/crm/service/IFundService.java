package com.example.crm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.dto.FundDetailResponse;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundFeatureData;
import com.example.crm.entity.FundNavHistory;
import com.example.crm.entity.FundStockHolding;

import java.util.List;

public interface IFundService {
    Page<CfgFund> page(long current, long size, String keyword, String fundType);

    FundDetailResponse detail(String fundCode);

    void create(CfgFund fund);

    void update(String fundCode, CfgFund fund);

    void delete(String fundCode);

    Page<FundNavHistory> navs(String fundCode, long current, long size);

    Page<FundStockHolding> holdings(String fundCode, long current, long size, String reportDate);

    List<FundFeatureData> features(String fundCode);
}
