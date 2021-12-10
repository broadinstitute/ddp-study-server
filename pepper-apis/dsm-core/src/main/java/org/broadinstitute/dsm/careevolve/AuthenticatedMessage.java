package org.broadinstitute.dsm.careevolve;

import com.google.gson.annotations.SerializedName;

public class AuthenticatedMessage {

    @SerializedName("request")
    private Message message;

    @SerializedName("authentication")
    private Authentication authentication;

    public AuthenticatedMessage(Authentication auth, Message message) {
        this.authentication = auth;
        this.message = message;
    }
}
