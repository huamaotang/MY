package com.example.crm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class CrmGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmGatewayApplication.class, args);
    }
}
