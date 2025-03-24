package com.clevercloud.testcontainers.warp10;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenInfo {
    @JsonProperty("token")
    private String token;

    @JsonProperty("ident")
    private String ident;

    @JsonProperty("id")
    private String id;

    // Getters and Setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getIdent() {
        return ident;
    }

    public void setIdent(String ident) {
        this.ident = ident;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "TokenInfo{" +
            "token='" + token + '\'' +
            ", ident='" + ident + '\'' +
            ", id='" + id + '\'' +
            '}';
    }
}
