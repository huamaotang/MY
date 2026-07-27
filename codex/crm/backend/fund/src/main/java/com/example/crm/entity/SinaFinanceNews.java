package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sina_finance_news")
public class SinaFinanceNews {
    private Long id;
    private String newsId;
    private Integer categoryTag;
    private String categoryName;
    private String content;
    private LocalDateTime createTime;
    private LocalDateTime sourceUpdateTime;
    private String docUrl;
    private String tagsJson;
    private String imagesJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
