package com.example.crm.android;

import org.json.JSONObject;
import java.io.Serializable;

public class StockQuote implements Serializable {
    public String stockCode, stockName, exchangeName, tradeDate, quoteTime, updatedAt, comment;
    public String latestPrice, changeRate, changeAmount, volume, amount, amplitude, turnoverRate;
    public String peDynamic, peTtm, volumeRatio, highPrice, lowPrice, openPrice, previousClose;
    public String totalMarketCap, floatMarketCap, pbRatio, changeRate60d, changeRateYtd;

    public static StockQuote fromJson(JSONObject json) {
        StockQuote s = new StockQuote();
        s.stockCode = Fund.optionalString(json, "stockCode"); s.stockName = Fund.optionalString(json, "stockName");
        s.exchangeName = Fund.optionalString(json, "exchangeName"); s.tradeDate = Fund.optionalString(json, "tradeDate");
        s.quoteTime = Fund.optionalString(json, "quoteTime"); s.updatedAt = Fund.optionalString(json, "updatedAt");
        s.comment = Fund.optionalString(json, "comment");
        s.latestPrice = Fund.optionalString(json, "latestPrice");
        s.changeRate = Fund.optionalString(json, "changeRate"); s.changeAmount = Fund.optionalString(json, "changeAmount");
        s.volume = Fund.optionalString(json, "volume"); s.amount = Fund.optionalString(json, "amount");
        s.amplitude = Fund.optionalString(json, "amplitude"); s.turnoverRate = Fund.optionalString(json, "turnoverRate");
        s.peDynamic = Fund.optionalString(json, "peDynamic"); s.peTtm = Fund.optionalString(json, "peTtm");
        s.volumeRatio = Fund.optionalString(json, "volumeRatio"); s.highPrice = Fund.optionalString(json, "highPrice");
        s.lowPrice = Fund.optionalString(json, "lowPrice"); s.openPrice = Fund.optionalString(json, "openPrice");
        s.previousClose = Fund.optionalString(json, "previousClose"); s.totalMarketCap = Fund.optionalString(json, "totalMarketCap");
        s.floatMarketCap = Fund.optionalString(json, "floatMarketCap"); s.pbRatio = Fund.optionalString(json, "pbRatio");
        s.changeRate60d = Fund.optionalString(json, "changeRate60d"); s.changeRateYtd = Fund.optionalString(json, "changeRateYtd");
        return s;
    }
}
