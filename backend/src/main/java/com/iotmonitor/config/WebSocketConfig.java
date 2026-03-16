package com.iotmonitor.config;

import com.iotmonitor.websocket.ReverseLinkWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ReverseLinkWebSocketHandler reverseLinkWebSocketHandler;

    public WebSocketConfig(ReverseLinkWebSocketHandler reverseLinkWebSocketHandler) {
        this.reverseLinkWebSocketHandler = reverseLinkWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(reverseLinkWebSocketHandler, "/reverselink/ws").setAllowedOrigins("*");
    }
}
