package com.example.slam;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SlamWebSocketHandler slamWebSocketHandler;

    public WebSocketConfig(SlamWebSocketHandler slamWebSocketHandler) {
        this.slamWebSocketHandler = slamWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(slamWebSocketHandler, "/slam").setAllowedOrigins("*");
    }
}

