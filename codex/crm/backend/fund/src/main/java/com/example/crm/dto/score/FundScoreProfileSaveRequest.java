package com.example.crm.dto.score;

import lombok.Data;

import java.util.Map;

@Data
public class FundScoreProfileSaveRequest {
    private String profileName;
    private Map<String, Integer> weights;
}
