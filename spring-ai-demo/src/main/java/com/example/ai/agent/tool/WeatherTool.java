package com.example.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class WeatherTool {

    public String execute(String city) {
        Map<String, String> weatherData = Map.of(
                "北京", "晴, 28°C, 湿度 45%",
                "上海", "多云, 25°C, 湿度 60%",
                "深圳", "雷阵雨, 30°C, 湿度 75%",
                "广州", "小雨, 27°C, 湿度 70%"
        );
        return weatherData.getOrDefault(city, "暂无" + city + "的天气数据");
    }
}
