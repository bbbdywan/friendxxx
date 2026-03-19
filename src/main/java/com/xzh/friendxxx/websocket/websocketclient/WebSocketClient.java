package com.xzh.friendxxx.websocket.websocketclient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.concurrent.ExecutionException;

@Component
public class WebSocketClient {

    // ws://127.0.0.1:9090/websocket/1
    @Value("${websocket.server.url}")
    private String websocketServerUrl;

    public void connect() throws ExecutionException, InterruptedException {
        StandardWebSocketClient client = new StandardWebSocketClient();
        String uri = websocketServerUrl; // 直接使用String类型的URL
        
        WebSocketConnectionManager manager = new WebSocketConnectionManager(client, new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                System.out.println("Connected to the WebSocket server");
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, org.springframework.web.socket.TextMessage message) throws Exception {
                System.out.println("Received message: " + message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                System.out.println("Connection closed");
            }
        }, uri); // 传入String类型的uri
        
        manager.start();
    }
}