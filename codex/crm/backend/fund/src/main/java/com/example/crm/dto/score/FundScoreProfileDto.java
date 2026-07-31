package com.example.crm.dto.score;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class FundScoreProfileDto {
    private Long id;
    private String profileName;
    private Integer versionNo;
    private String status;
    private String sourceType;
    private Integer targetMonths;
    private Map<String, Integer> weights;
    private String validationStatus;
    private Boolean active;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
