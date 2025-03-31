package com.clevercloud.testcontainers.warp10;

import java.util.List;

public class Warp10Tokens {
    private List<TokenInfo> tokens;

    public Warp10Tokens(TokenInfo[] tokens) {
        this.tokens = List.of(tokens);
    }

    public List<TokenInfo> getTokens() {
        return tokens;
    }

    public TokenInfo getWriteToken() {
        return tokens.stream()
            .filter(token -> "WriteToken".equalsIgnoreCase(token.getId()))
            .findFirst()
            .orElse(null);
    }

    public TokenInfo getReadToken() {
        return tokens.stream()
            .filter(token -> "ReadToken".equalsIgnoreCase(token.getId()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public String toString() {
        return "Warp10Tokens{" +
            "tokens=" + tokens +
            '}';
    }
}
