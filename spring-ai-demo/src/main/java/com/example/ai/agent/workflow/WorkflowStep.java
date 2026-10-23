package com.example.ai.agent.workflow;

import com.example.ai.agent.tool.AgentTool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep {

    private String name;
    private String description;
    private String prompt;
    private List<AgentTool> tools;
    private boolean requiresToolCall;
}
