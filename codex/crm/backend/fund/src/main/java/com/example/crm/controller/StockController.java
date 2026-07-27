package com.example.crm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.*;

@RestController
@RequestMapping("/stocks")
public class StockController {
    private static final Map<String, String> SORT_FIELDS = new HashMap<>();
    static {
        SORT_FIELDS.put("stockCode", "d.stock_code");
        SORT_FIELDS.put("stockName", "d.stock_name");
        SORT_FIELDS.put("latestPrice", "h.latest_price");
        SORT_FIELDS.put("changeRate", "h.change_rate");
        SORT_FIELDS.put("changeAmount", "h.change_amount");
        SORT_FIELDS.put("volume", "h.volume");
        SORT_FIELDS.put("amount", "h.amount");
        SORT_FIELDS.put("amplitude", "h.amplitude");
        SORT_FIELDS.put("turnoverRate", "h.turnover_rate");
        SORT_FIELDS.put("volumeRatio", "h.volume_ratio");
        SORT_FIELDS.put("peDynamic", "h.pe_dynamic");
        SORT_FIELDS.put("pbRatio", "h.pb_ratio");
        SORT_FIELDS.put("totalMarketCap", "h.total_market_cap");
        SORT_FIELDS.put("floatMarketCap", "h.float_market_cap");
        SORT_FIELDS.put("changeRate60d", "h.change_rate_60d");
        SORT_FIELDS.put("changeRateYtd", "h.change_rate_ytd");
    }

    private final JdbcTemplate jdbc;
    public StockController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer marketCode,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        long safeSize = Math.min(Math.max(size, 1), 200);
        long safeCurrent = Math.max(current, 1);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE h.trade_date=(SELECT MAX(trade_date) FROM stock_daily_history)");
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (d.stock_code LIKE ? OR d.stock_name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value);
        }
        if (marketCode != null) {
            where.append(" AND d.market_code=?");
            args.add(marketCode);
        }
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_detail d JOIN stock_daily_history h ON h.stock_code=d.stock_code" + where,
                Long.class, args.toArray());
        String orderColumn = SORT_FIELDS.getOrDefault(sortField, "d.stock_code");
        String direction = "ascend".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        if (sortField == null || sortField.trim().isEmpty()) direction = "ASC";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize); pageArgs.add((safeCurrent - 1) * safeSize);
        String fields = stockFields();
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT " + fields + " FROM stock_detail d JOIN stock_daily_history h ON h.stock_code=d.stock_code" +
                        where + " ORDER BY " + orderColumn + " " + direction + ",d.stock_code ASC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Page<Map<String, Object>> page = new Page<>(safeCurrent, safeSize, total == null ? 0 : total);
        page.setRecords(records);
        return ApiResponse.ok(page);
    }

    @GetMapping("/{stockCode}")
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String stockCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + stockFields() + " FROM stock_detail d LEFT JOIN stock_daily_history h ON h.stock_code=d.stock_code " +
                        "AND h.trade_date=(SELECT MAX(x.trade_date) FROM stock_daily_history x WHERE x.stock_code=d.stock_code) WHERE d.stock_code=?",
                stockCode);
        if (rows.isEmpty()) throw new IllegalArgumentException("股票不存在: " + stockCode);
        return ApiResponse.ok(rows.get(0));
    }

    @GetMapping("/{stockCode}/history")
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<Page<Map<String, Object>>> history(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Date startDate,
            @RequestParam(required = false) Date endDate) {
        long safeSize = Math.min(Math.max(size, 1), 200);
        long safeCurrent = Math.max(current, 1);
        StringBuilder where = new StringBuilder(" WHERE stock_code=?");
        List<Object> args = new ArrayList<>(); args.add(stockCode);
        if (startDate != null) { where.append(" AND trade_date>=?"); args.add(startDate); }
        if (endDate != null) { where.append(" AND trade_date<=?"); args.add(endDate); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM stock_daily_history" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize); pageArgs.add((safeCurrent - 1) * safeSize);
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT id,stock_code AS stockCode,trade_date AS tradeDate,quote_time AS quoteTime," +
                        historyFields() + " FROM stock_daily_history" + where +
                        " ORDER BY trade_date DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        Page<Map<String, Object>> page = new Page<>(safeCurrent, safeSize, total == null ? 0 : total);
        page.setRecords(records);
        return ApiResponse.ok(page);
    }

    private static String stockFields() {
        return "d.id,d.stock_code AS stockCode,d.stock_name AS stockName,d.market_code AS marketCode," +
                "d.exchange_name AS exchangeName,d.listing_date AS listingDate,h.trade_date AS tradeDate," +
                "h.quote_time AS quoteTime," + historyFields("h.");
    }

    private static String historyFields() { return historyFields(""); }
    private static String historyFields(String p) {
        return p+"latest_price AS latestPrice,"+p+"change_rate AS changeRate,"+p+"change_amount AS changeAmount,"+
                p+"volume,"+p+"amount,"+p+"amplitude,"+p+"turnover_rate AS turnoverRate,"+
                p+"pe_dynamic AS peDynamic,"+p+"volume_ratio AS volumeRatio,"+
                p+"five_min_change_rate AS fiveMinChangeRate,"+p+"high_price AS highPrice,"+
                p+"low_price AS lowPrice,"+p+"open_price AS openPrice,"+p+"previous_close AS previousClose,"+
                p+"total_market_cap AS totalMarketCap,"+p+"float_market_cap AS floatMarketCap,"+
                p+"speed_rate AS speedRate,"+p+"pb_ratio AS pbRatio,"+
                p+"change_rate_60d AS changeRate60d,"+p+"change_rate_ytd AS changeRateYtd,"+
                p+"main_net_inflow AS mainNetInflow,"+p+"pe_ttm AS peTtm,"+
                p+"updated_at AS updatedAt,"+p+"`comment` AS comment";
    }
}
