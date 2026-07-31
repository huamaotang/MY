package com.example.crm.dto.score;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FundScoreJobDto {
    private Long id;
    private String jobType;
    private Long profileId;
    private String status;
    private String requestedBy;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
