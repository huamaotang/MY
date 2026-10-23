package com.example.ai.agent;

import com.example.ai.agent.dto.WorkflowRequest;
import com.example.ai.agent.dto.WorkflowResponse;
import com.example.ai.agent.tool.AgentTool;
import com.example.ai.agent.tool.CalculatorTool;
import com.example.ai.agent.tool.WeatherTool;
import com.example.ai.agent.workflow.AgentWorkflow;
import com.example.ai.agent.workflow.AgentWorkflowEngine;
import com.example.ai.agent.workflow.WorkflowStep;
import com.example.ai.agent.workflow.StepResult;
import com.example.ai.service.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentWorkflowController {

    private final AgentWorkflowEngine engine;
    private final WeatherTool weatherTool;
    private final CalculatorTool calculatorTool;
    private final OllamaService ollamaService;

    @PostMapping("/workflow")
    public WorkflowResponse executeWorkflow(@RequestBody WorkflowRequest request) {
        AgentWorkflow workflow = buildDefaultWorkflow(request);
        List<StepResult> results = engine.execute(workflow, request.getUserInput());

        boolean success = results.stream().allMatch(StepResult::isSuccess);
        String summary = results.stream()
                .map(StepResult::getOutput)
                .reduce((a, b) -> a + "\n--- " + b)
                .orElse("无结果");

        return WorkflowResponse.builder()
                .workflowName(workflow.getName())
                .success(success)
                .stepResults(results)
                .summary(summary)
                .build();
    }

    @PostMapping("/weather")
    public String getWeather(@RequestParam String city) {
        return weatherTool.execute(city);
    }

    @PostMapping("/calculate")
    public String calculate(@RequestParam String expression) {
        return calculatorTool.execute(expression);
    }

    @PostMapping("/chat")
    public String chat(@RequestBody WorkflowRequest request) {
        return ollamaService.chat(request.getUserInput(),
                "你是一个智能Agent工作流助手，可以处理天气查询、数学计算和综合任务。");
    }

    private AgentWorkflow buildDefaultWorkflow(WorkflowRequest request) {
        WorkflowStep analysisStep = WorkflowStep.builder()
                .name("分析任务")
                .description("分析用户输入并确定需要的工具")
                .prompt("请分析用户请求，确定需要执行哪些步骤和工具。")
                .tools(List.of(
                        AgentTool.builder().name("weather").description("查询天气").parameters("city").function(weatherTool::execute).build(),
                        AgentTool.builder().name("calculator").description("计算数学表达式").parameters("expression").function(calculatorTool::execute).build()
                ))
                .requiresToolCall(true)
                .build();

        WorkflowStep executionStep = WorkflowStep.builder()
                .name("执行任务")
                .description("执行分析后的任务步骤")
                .prompt("根据分析结果，执行具体任务并给出最终答案。")
                .requiresToolCall(false)
                .build();

        WorkflowStep summaryStep = WorkflowStep.builder()
                .name("总结回答")
                .description("总结所有步骤的结果并给出最终回答")
                .prompt("请将以上所有步骤的结果整合，给出一个连贯、完整的回答。")
                .requiresToolCall(false)
                .build();

        return AgentWorkflow.builder()
                .name(request.getWorkflowName() != null ? request.getWorkflowName() : "DefaultAgentWorkflow")
                .description("简单的Agent工作流，包含分析、执行和总结三个步骤")
                .steps(List.of(analysisStep, executionStep, summaryStep))
                .build();
    }
}
