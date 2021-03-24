package org.broadinstitute.ddp.security;

public class AuthResponse {

    private String ddpToken;

    public AuthResponse(String ddpToken) {
        this.ddpToken = ddpToken;
    }

    public String getDdpToken() {
        return ddpToken;
    }
}
