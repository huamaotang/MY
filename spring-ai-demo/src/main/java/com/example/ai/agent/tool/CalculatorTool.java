package com.example.ai.agent.tool;

import org.springframework.stereotype.Component;

@Component
public class CalculatorTool {

    public String execute(String expression) {
        try {
            expression = expression.replaceAll("\\s+", "");
            double result = evaluateExpression(expression);
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    private double evaluateExpression(String expr) {
        if (expr.contains("+")) {
            String[] parts = expr.split("\\+", 2);
            return Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]);
        }
        if (expr.contains("-")) {
            String[] parts = expr.split("-", 2);
            return Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]);
        }
        if (expr.contains("*")) {
            String[] parts = expr.split("\\*", 2);
            return Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]);
        }
        if (expr.contains("/")) {
            String[] parts = expr.split("/", 2);
            return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        }
        return Double.parseDouble(expr);
    }
}
