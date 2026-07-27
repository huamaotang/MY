package com.example.crm.entity;

import lombok.Data;

@Data
public class FundHoldingCandidate {
    private String fundCode;
    private String fundName;
    private Integer score;
}
