package org.broadinstitute.ddp.cf.json;

import com.google.gson.annotations.SerializedName;

public class IncomingRequest {

    @SerializedName("url")
    private final String url;

    @SerializedName("method")
    private final String method;

    @SerializedName("email")
    private final String email;

    @SerializedName("start")
    private final String start;

    @SerializedName("end")
    private final String end;

    @SerializedName("auth0Domain")
    private final String auth0Domain;

    @SerializedName("auth0ClientId")
    private final String auth0ClientId;

    @SerializedName("is_active")
    private final Boolean isActive;

    public IncomingRequest(String url, String method, String email, String start, String end, String auth0Domain,
                           String auth0ClientId, Boolean isActive) {
        this.url = url;
        this.method = method;
        this.email = email;
        this.start = start;
        this.end = end;
        this.isActive = isActive;
        this.auth0Domain = auth0Domain;
        this.auth0ClientId = auth0ClientId;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public String getEmail() {
        return email;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public String getAuth0Domain() {
        return auth0Domain;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }
}
