package com.fishmoun.soulroute.auth;

public record AuthRequest(String username, String password, String displayName) {
}
