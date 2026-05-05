package com.fishmoun.soulroute.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class TokenService {

    private final ObjectMapper objectMapper;
    private final String secret;
    private final long ttlSeconds;

    public TokenService(ObjectMapper objectMapper,
                        @Value("${soulroute.auth.token-secret}") String secret,
                        @Value("${soulroute.auth.token-ttl-hours:168}") long ttlHours) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.ttlSeconds = ttlHours * 3600;
    }

    public String create(AuthUser user) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uid", user.id());
            payload.put("username", user.username());
            payload.put("exp", expiresAt);
            String body = base64Url(objectMapper.writeValueAsBytes(payload));
            String signature = sign(body);
            return body + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("生成登录 token 失败", e);
        }
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            long expiresAt = node.path("exp").asLong(0);
            if (expiresAt < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(
                    node.path("uid").asLong(),
                    node.path("username").asText(),
                    expiresAt
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名 token 失败", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
