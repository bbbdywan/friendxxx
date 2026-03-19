package com.xzh.friendxxx.websocket.websocketserver;

import com.xzh.friendxxx.websocket.websocketserver.server.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@EnableScheduling
@Slf4j
@ConditionalOnProperty(name = "websocket.heartbeat.enabled", havingValue = "true", matchIfMissing = false)
public class Send {

    @Scheduled(fixedDelay = 30000) // 30秒发送一次心跳
    public void sendHeartbeat() {
        try {
            String heartbeatMsg = "心跳检测: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            if (WebSocketServer.getOnlineCount() > 0) {
                WebSocketServer server = new WebSocketServer();
                server.sendAllMessage(heartbeatMsg);
                log.debug("发送心跳消息给{}个在线用户", WebSocketServer.getOnlineCount());
            }
        } catch (IOException e) {
            log.error("发送心跳消息失败", e);
        }
    }
}