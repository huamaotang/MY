package com.example.crm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.dto.FundDailyValuationDto;
import com.example.crm.dto.FundDetailResponse;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundFeatureData;
import com.example.crm.entity.FundNavHistory;
import com.example.crm.entity.FundRating;
import com.example.crm.entity.FundStockHolding;

import java.util.List;

public interface IFundService {
    Page<CfgFund> page(String ownerUsername, long current, long size, String keyword, String fundType,
                       Boolean canBuy, boolean favoritesOnly, String sortField, String sortOrder);

    void addFavorite(String ownerUsername, String fundCode);

    void removeFavorite(String ownerUsername, String fundCode);

    FundDetailResponse detail(String fundCode);

    void create(CfgFund fund);

    void update(String fundCode, CfgFund fund);

    void delete(String fundCode);

    Page<FundNavHistory> navs(String fundCode, long current, long size);

    Page<FundStockHolding> holdings(String fundCode, long current, long size, String reportDate);

    Page<FundDailyValuationDto> valuations(String fundCode, long current, long size);

    List<FundFeatureData> features(String fundCode);

    List<FundRating> ratings(String fundCode);
}
