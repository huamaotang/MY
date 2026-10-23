package com.example.ai.agent.workflow;

import com.example.ai.agent.tool.AgentTool;
import com.example.ai.service.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentWorkflowEngine {

    private final OllamaService ollamaService;

    public List<StepResult> execute(AgentWorkflow workflow, String userInput) {
        List<StepResult> results = new ArrayList<>();
        String context = userInput;

        for (WorkflowStep step : workflow.getSteps()) {
            StepResult result = executeStep(step, context);
            results.add(result);
            context = result.getOutput();
        }

        return results;
    }

    private StepResult executeStep(WorkflowStep step, String context) {
        try {
            String prompt = buildPrompt(step, context);

            if (step.isRequiresToolCall() && step.getTools() != null) {
                String toolResult = tryToolCall(step, context);
                if (toolResult != null) {
                    String llmResponse = callLlmWithToolResult(step, toolResult);
                    return StepResult.builder()
                            .stepName(step.getName())
                            .output(llmResponse)
                            .success(true)
                            .build();
                }
            }

            String llmResponse = callLlm(step, context);
            return StepResult.builder()
                    .stepName(step.getName())
                    .output(llmResponse)
                    .success(true)
                    .build();
        } catch (Exception e) {
            return StepResult.builder()
                    .stepName(step.getName())
                    .output(null)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    private String buildPrompt(WorkflowStep step, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append(step.getPrompt()).append("\n\n");
        sb.append("上下文: ").append(context).append("\n\n");
        if (step.getTools() != null && !step.getTools().isEmpty()) {
            sb.append("可用工具: ").append(
                    step.getTools().stream().map(AgentTool::getName).collect(Collectors.joining(", "))
            ).append("\n");
        }
        return sb.toString();
    }

    private String tryToolCall(WorkflowStep step, String context) {
        for (AgentTool tool : step.getTools()) {
            if (context.toLowerCase().contains(tool.getName().toLowerCase())
                    || matchesToolCondition(tool, context)) {
                return tool.getFunction().execute(context);
            }
        }
        return null;
    }

    private boolean matchesToolCondition(AgentTool tool, String context) {
        if (tool.getParameters() == null) return false;
        String params = tool.getParameters().toLowerCase();
        return context.toLowerCase().contains(params);
    }

    private String callLlm(WorkflowStep step, String context) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("你是一个智能助手，请按步骤完成任务。"),
                new UserMessage(buildPrompt(step, context))
        ));
        return ollamaService.callOllama(prompt);
    }

    private String callLlmWithToolResult(WorkflowStep step, String toolResult) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("基于工具返回的结果，请给出简洁的回答。"),
                new UserMessage("工具结果: " + toolResult + "\n\n请总结并回答。")
        ));
        return ollamaService.callOllama(prompt);
    }
}
