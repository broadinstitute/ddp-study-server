package org.broadinstitute.ddp.security;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The guts of our logic for figuring out whether
 * a user should be able to access various things
 * like profile, study, or participant data.
 */
public class UserPermissions {

    private static final Logger LOG = LoggerFactory.getLogger(UserPermissions.class);

    private final Collection<String> clientStudyGuids;

    private final Collection<ParticipantAccess> participantAccesses;

    private final boolean isAccountLocked;

    private final boolean isClientRevoked;

    private final String operatorGuid;

    private final Collection<String> adminStudiesWithAccess;

    /**
     * Instantiates UserPermissions object.
     */
    public UserPermissions(String operatorGuid,
                           boolean isAccountLocked,
                           boolean isClientRevoked,
                           Collection<String> clientStudyGuids,
                           Collection<ParticipantAccess> participantAccesses,
                           Collection<String> adminStudiesWithAccess) {
        this.operatorGuid = operatorGuid;
        this.isAccountLocked = isAccountLocked;
        this.isClientRevoked = isClientRevoked;
        this.participantAccesses = participantAccesses;
        this.adminStudiesWithAccess = adminStudiesWithAccess;
        this.clientStudyGuids = clientStudyGuids;
    }

    /**
     * Determines whether this user can access the given user's
     * list of governed users.
     */
    public boolean canAccessGovernedUsers(String requestedUserGuid) {
        // you can only access a user's list of governed users if you *are* that governed user.
        // user A cannot alter user B's list of governed users
        boolean canAccess = false;
        if (StringUtils.isNoneBlank(operatorGuid, requestedUserGuid) && !isDisabled()) {
            canAccess = operatorGuid.equals(requestedUserGuid);
        }
        return canAccess;
    }

    /**
     * Returns whether or not this user's account or the client
     * used to sign them in has been locked or revoked.
     */
    public boolean isDisabled() {
        return isClientRevoked || isAccountLocked;
    }

    /**
     * Checks if the client that user is accessing with has permissions to the requested study.
     */
    public boolean isStudyPermittedForUserClient(String studyGuid) {
        return clientStudyGuids.contains(studyGuid);
    }

    /**
     * Returns whether or not this user can access study data for
     * study studyGuid for user requestedUserGuid.
     */
    public boolean canAccessStudyDataForUser(String requestedUserGuid, String studyGuid) {
        if (isDisabled()) {
            LOG.warn("Either client is revoked or account is locked for user: {} and study: {}", requestedUserGuid, studyGuid);
            return false;
        } else if (!isStudyPermittedForUserClient(studyGuid)) {
            LOG.warn("Study GUID: {} is not one of the studies enabled for given Auth0 client", studyGuid);
            return false;
        } else {
            // if the operator is the requested user, they can do anything to their own data
            if (operatorGuid.equals(requestedUserGuid)) {
                LOG.debug("Operator is the requested user");
                return true;
            } else if (hasAdminAccessToStudy(studyGuid)) {
                LOG.debug("Operator has admin access to study {}", studyGuid);
                return true;
            } else {
                LOG.debug("The requested user GUID: {} is not the same as the operator GUID. About to check if this a managed user",
                        requestedUserGuid);
                // verify that this operator has been granted access to the participant
                // in the context of a particular study
                for (ParticipantAccess accessForParticipant : participantAccesses) {
                    if (accessForParticipant.getParticipantGuid().equals(requestedUserGuid)) {
                        for (String allowedStudy : accessForParticipant.getStudyAccess()) {
                            if (studyGuid.equals(allowedStudy)) {
                                LOG.debug("The requested user GUID: {} is managed", requestedUserGuid);
                                return true;
                            }
                        }

                    }
                }
                LOG.warn("The requested user GUID: {} is not managed", requestedUserGuid);
                return false;
            }
        }
    }

    /**
     * Returns whether or not this user can access
     * the profile for user requestedUserGuid.
     */
    public boolean canAccessUserProfile(Handle handle, String requestedUserGuid) {
        // A user may access another user's profile if the other user is
        // in their list of governed users, or the user is a study admin.
        if (!isDisabled()) {
            boolean canAccess = operatorGuid.equals(requestedUserGuid);
            if (!canAccess) {
                for (ParticipantAccess accessForParticipant : participantAccesses) {
                    if (accessForParticipant.getParticipantGuid().equals(requestedUserGuid)) {
                        canAccess = true;
                        break;
                    }
                }
            }

            // Check if it's a study admin and if requested user is in a study they have access to.
            if (!canAccess && isAdmin()) {
                canAccess = handle.attach(JdbiUserStudyEnrollment.class)
                        .getAllLatestEnrollmentsForUser(requestedUserGuid)
                        .stream()
                        .filter(status -> !status.getEnrollmentStatus().isExited())
                        .map(EnrollmentStatusDto::getStudyGuid)
                        .anyMatch(adminStudiesWithAccess::contains);
            }

            return canAccess;
        }
        return false;
    }

    /**
     * Returns whether or not this user has admin access
     * to a study.
     */
    public boolean hasAdminAccessToStudy(String requestedStudyGuid) {
        if (!isDisabled() && adminStudiesWithAccess.contains(requestedStudyGuid)) {
            return true;
        }
        return false;
    }

    /**
     * Returns if user is an admin of any studies.
     */
    public boolean isAdmin() {
        if (!isDisabled() && adminStudiesWithAccess != null && !adminStudiesWithAccess.isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean canUpdateLoginData(String requestedUserGuid) {
        return operatorGuid.equals(requestedUserGuid);
    }

}
