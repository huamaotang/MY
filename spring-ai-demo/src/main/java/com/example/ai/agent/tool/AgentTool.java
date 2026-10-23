package com.example.ai.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTool {

    private String name;
    private String description;
    private String parameters;
    private ToolFunction function;

    @FunctionalInterface
    public interface ToolFunction {
        String execute(String input);
    }
}
