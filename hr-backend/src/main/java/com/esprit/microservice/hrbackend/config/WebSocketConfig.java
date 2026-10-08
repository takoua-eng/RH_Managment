package com.esprit.microservice.hrbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;

    /**
     * Adresses autorisées à ouvrir une connexion WebSocket.
     * En local : http://localhost:4200 (valeur par défaut d'application.properties).
     * Dans le cluster : fournie par la variable d'environnement ALLOWED_ORIGINS.
     */
    @Value("${app.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("[WebSocket] Origines autorisées : {}", String.join(", ", allowedOrigins));

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    String token = null;

                    if (authorization != null && !authorization.isEmpty()) {
                        String bearerToken = authorization.get(0);
                        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                            token = bearerToken.substring(7);
                        }
                    }

                    if (!StringUtils.hasText(token)) {
                        List<String> accessTokenHeader = accessor.getNativeHeader("access_token");
                        if (accessTokenHeader != null && !accessTokenHeader.isEmpty()) {
                            token = accessTokenHeader.get(0);
                        }
                    }

                    if (!StringUtils.hasText(token)) {
                        log.warn("[WebSocket] Connection rejected: Missing Authorization Bearer token");
                        throw new MessageDeliveryException("Missing Authorization header");
                    }

                    try {
                        Jwt jwt = jwtDecoder.decode(token);
                        String keycloakId = jwt.getSubject();

                        if (!StringUtils.hasText(keycloakId)) {
                            log.warn("[WebSocket] Connection rejected: JWT subject is empty");
                            throw new MessageDeliveryException("Invalid JWT subject");
                        }

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(keycloakId, null, Collections.emptyList());
                        accessor.setUser(authentication);
                        log.debug("[WebSocket] Session connected successfully for KeycloakId: {}", keycloakId);
                    } catch (Exception e) {
                        log.error("[WebSocket] Connection rejected: Invalid JWT token - {}", e.getMessage());
                        throw new MessageDeliveryException("Unauthorized STOMP connection: " + e.getMessage());
                    }
                }
                return message;
            }
        });
    }
}