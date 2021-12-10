package org.broadinstitute.lddp.security;

public class AuthResponse {

    private String ddpToken;

    public AuthResponse(String ddpToken) {
        this.ddpToken = ddpToken;
    }

    public String getDdpToken() {
        return ddpToken;
    }
}
