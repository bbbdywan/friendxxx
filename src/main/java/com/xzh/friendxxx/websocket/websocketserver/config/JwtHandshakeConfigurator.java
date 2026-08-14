package com.xzh.friendxxx.websocket.websocketserver.config;

import com.xzh.friendxxx.service.JwtService;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class JwtHandshakeConfigurator extends ServerEndpointConfig.Configurator {

    public static final String ORIGIN_ALLOWED_KEY = "websocket.origin.allowed";
    public static final String USER_ID_KEY = "websocket.user-id";
    private static JwtService jwtService;

    @Autowired
    public void setJwtService(JwtService jwtService) {
        JwtHandshakeConfigurator.jwtService = jwtService;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig config,
                                HandshakeRequest request,
                                HandshakeResponse response) {
        Long userId = resolveUserId(request.getQueryString());
        if (userId != null) {
            config.getUserProperties().put(USER_ID_KEY, userId);
        }
        config.getUserProperties().put(ORIGIN_ALLOWED_KEY, isOriginAllowed(request.getHeaders()));
    }

    private Long resolveUserId(String queryString) {
        if (jwtService == null || queryString == null || queryString.isBlank()) {
            return null;
        }
        return Arrays.stream(queryString.split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2 && "token".equals(pair[0]))
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .map(jwtService::resolveUserId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean isOriginAllowed(Map<String, List<String>> headers) {
        String origin = headers.entrySet().stream()
                .filter(entry -> "origin".equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse("");
        if (origin.isBlank()) {
            return true;
        }
        String configured = System.getenv().getOrDefault(
                "APP_CORS_ALLOWED_ORIGINS",
                "http://localhost,http://localhost:5173,http://127.0.0.1:5173,capacitor://localhost,https://localhost");
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .anyMatch(origin::equals);
    }
}
