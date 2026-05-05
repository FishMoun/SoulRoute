package com.fishmoun.soulroute.auth;

public record TokenClaims(Long userId, String username, long expiresAtEpochSecond) {
}
