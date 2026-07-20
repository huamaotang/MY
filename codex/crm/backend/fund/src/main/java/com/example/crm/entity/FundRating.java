package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fund_rating")
public class FundRating {
    private Long id;
    private String fundCode;
    private String ratingDate;
    private Integer zhaoshangRating;
    @TableField("shanghai_rating_3y")
    private Integer shanghaiRating3y;
    @TableField("shanghai_rating_5y")
    private Integer shanghaiRating5y;
    private Integer jianRating;
    private Integer morningStarRating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
