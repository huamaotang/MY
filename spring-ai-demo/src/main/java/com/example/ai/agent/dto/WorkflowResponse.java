package com.example.ai.agent.dto;

import com.example.ai.agent.workflow.StepResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {

    private String workflowName;
    private boolean success;
    private List<StepResult> stepResults;
    private String summary;
}
