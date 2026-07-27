package com.example.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.ApiResponse;
import com.example.crm.entity.SinaFinanceNews;
import com.example.crm.mapper.SinaFinanceNewsMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/news")
public class FinanceNewsController {
    private final SinaFinanceNewsMapper mapper;
    public FinanceNewsController(SinaFinanceNewsMapper mapper) { this.mapper = mapper; }

    @GetMapping
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<Page<SinaFinanceNews>> page(@RequestParam(defaultValue="1") long current,
                                                   @RequestParam(defaultValue="20") long size,
                                                   @RequestParam(required=false) String keyword,
                                                   @RequestParam(required=false) Integer categoryTag) {
        LambdaQueryWrapper<SinaFinanceNews> query = new LambdaQueryWrapper<SinaFinanceNews>()
                .like(keyword != null && !keyword.trim().isEmpty(), SinaFinanceNews::getContent, keyword == null ? null : keyword.trim())
                .eq(categoryTag != null, SinaFinanceNews::getCategoryTag, categoryTag)
                .orderByDesc(SinaFinanceNews::getCreateTime);
        return ApiResponse.ok(mapper.selectPage(new Page<>(current, size), query));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('fund:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) { mapper.deleteById(id); return ApiResponse.ok(); }
}
