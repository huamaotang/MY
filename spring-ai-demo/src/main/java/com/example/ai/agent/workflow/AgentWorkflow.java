package com.example.ai.agent.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkflow {

    private String name;
    private String description;
    private List<WorkflowStep> steps;
}
