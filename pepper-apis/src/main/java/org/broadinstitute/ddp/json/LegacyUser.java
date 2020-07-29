package org.broadinstitute.ddp.json;
import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class LegacyUser {
    @SerializedName("email")
    private String email;

    @SerializedName("email_verified")
    private Boolean emailVerified = true;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("given_name")
    private String givenName;

    @SerializedName("family_name")
    private String familyName;

    @SerializedName("name")
    private String name;

    @SerializedName("password_hash")
    private String passwordHash;

    @SerializedName("app_metadata")
    private Map<String, Object> appMetadata;

    public LegacyUser(String email, String userId,
                      String givenName, String familyName, String passwordHash,
                      Map<String, Object> appMetadata) {
        this.email = email;
        this.userId = userId;
        this.givenName = givenName;
        this.familyName = familyName;
        this.name = givenName + " " + familyName;
        this.passwordHash = passwordHash;
        this.appMetadata = appMetadata;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Map<String, Object> getAppMetadata() {
        return appMetadata;
    }

    public void setAppMetadata(Map<String, Object> appMetadata) {
        this.appMetadata = appMetadata;
    }
}
