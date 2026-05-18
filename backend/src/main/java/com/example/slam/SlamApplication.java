package com.example.slam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SlamApplication {
    public static void main(String[] args) {
        SpringApplication.run(SlamApplication.class, args);
    }
}

