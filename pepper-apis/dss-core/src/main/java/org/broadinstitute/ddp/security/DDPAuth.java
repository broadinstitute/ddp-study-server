package org.broadinstitute.ddp.security;

import java.io.Serializable;
import java.util.Locale;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.Handle;

/**
 * Access control object for a user. The existence
 * of an instance of this class is not sufficient for
 * ACL checks.
 * Use #{@link #canAccessStudyDataForUser(String, String)} to confirm
 * they have access to a given user's data in a given study.
 * Use #{@link #canAccessUserProfile(Handle, String)} to confirm they
 * can access the profile for a given user.
 * Use #{@link #isActive()} to determine whether this user
 * has been disabled
 */
public class DDPAuth implements Serializable {

    private String domain = null;
    private String client = null;

    /**
     * user.guid for the operator, NOT auth0 user id
     */
    private String operator = null;

    private String token = null;

    private UserPermissions userPermissions;

    private String preferredLanguage;

    public DDPAuth() {
    }

    /**
     * Instantiates DDPAuth object.
     */
    public DDPAuth(
            String domain, String clientGuid, String operatorGuid, String token,
            UserPermissions userPermissions, String preferredLanguage
    ) {
        this.domain = domain;
        this.client = clientGuid;
        this.operator = operatorGuid;
        this.token = token;
        this.userPermissions = userPermissions;
        this.preferredLanguage = preferredLanguage;
    }

    public DDPAuth(
            String issuer, String clientGuid, String operatorGuid,
            UserPermissions userPermissions, String preferredLanguage
    ) {
        this(issuer, clientGuid, operatorGuid, null, userPermissions, preferredLanguage);
    }

    /**
     * Determines whether this user can access the profile for the given userGuid.
     *
     * @param handle   the database handle
     * @param userGuid the guid of the user who's profile you'd like to access
     */
    public boolean canAccessUserProfile(Handle handle, String userGuid) {
        if (userPermissions != null) {
            return userPermissions.canAccessUserProfile(handle, userGuid);
        } else {
            return false;
        }
    }

    /**
     * Determines whether this user can access data for a different
     * user in the context of a given study.
     *
     * @param userGuid  the guid of the user who's data you want to access
     * @param studyGuid the guid for the study
     */
    public boolean canAccessStudyDataForUser(String userGuid, String studyGuid) {
        if (userPermissions != null) {
            return userPermissions.canAccessStudyDataForUser(userGuid, studyGuid);
        } else {
            return false;
        }
    }

    public String getOperator() {
        return operator;
    }

    public String getToken() {
        return token;
    }

    public String getDomain() {
        return domain;
    }

    /**
     * Checks whether this user's account or the client used to login
     * the account have been locked or revoked.  Under normal use,
     * this should always return true.  Only in the event of a security
     * issue will a user's account or a client be locked/revoked.
     *
     * @return true if the user's account and client are in good
     *      standing; false if the user's account has been locked
     *      or if the client used to login the account has been revoked.
     */
    public boolean isActive() {
        if (userPermissions != null) {
            return !userPermissions.isDisabled();
        } else {
            return false;
        }

    }

    /**
     * Returns true if the user is considered active, and a valid
     * token was sent with their request
     * @return true if the session is authenticated
     */
    public boolean isAuthenticated() {
        return (StringUtils.isNotBlank(token))
            && isActive();
    }

    public String getClient() {
        return client;
    }

    /**
     * Checks whether this user can access a specific governed user.
     */
    public boolean canAccessGovernedUsers(String requestedUserGuid) {
        if (userPermissions != null) {
            return userPermissions.canAccessGovernedUsers(requestedUserGuid);
        } else {
            return false;
        }
    }

    public boolean canUpdateLoginData(String requestedUserGuid) {
        return userPermissions != null ? userPermissions.canUpdateLoginData(requestedUserGuid) : false;
    }

    /**
     * Checks whether user has access to specific study.
     */
    public boolean hasAdminAccessToStudy(String studyGuid) {
        if (userPermissions != null) {
            return userPermissions.hasAdminAccessToStudy(studyGuid);
        } else {
            return false;
        }
    }

    /**
     * Checks whether user has admin access to any studies.
     */
    public boolean isAdmin() {
        if (userPermissions != null) {
            return userPermissions.isAdmin();
        }
        return false;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String languageCode) {
        preferredLanguage = languageCode;
    }

    @Nullable
    public Locale getPreferredLocale() {
        String preferredLanguage = getPreferredLanguage();

        if (StringUtils.isBlank(preferredLanguage)) {
            return null;
        }

        return Locale.forLanguageTag(preferredLanguage);
    }
}
