package org.broadinstitute.ddp.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DDPAuthTest {

    public static final String LOGGED_IN_OPERATOR_GUID = "operator456";
    public static final String OTHER_USER_GUID = "other123";
    public static final String STUDY_WITH_ACCESS = "the study";
    public static final String CLIENT_GUID = "c123";
    public static final String PREFERRED_LANGUAGE = "en";
    public static final String ISSUER = "https://test.issuer.example/";

    private DDPAuth userWithAccessToOneParticipantInOneStudy;

    private DDPAuth userWithRevokedClient;

    private DDPAuth userWithLockedAccount;

    @Before
    public void setupAuth() {
        Collection<ParticipantAccess> aclForOtherParticipants = new ArrayList<>();
        ParticipantAccess accessToOtherUser = new ParticipantAccess(OTHER_USER_GUID);
        accessToOtherUser.addStudyGuid(STUDY_WITH_ACCESS);
        aclForOtherParticipants.add(accessToOtherUser);

        Collection<String> aclForClientStudy = new ArrayList<>();
        aclForClientStudy.add(STUDY_WITH_ACCESS);

        UserPermissions userPermissions = new UserPermissions(LOGGED_IN_OPERATOR_GUID, false,
                false, aclForClientStudy, aclForOtherParticipants, new ArrayList<>());
        userWithAccessToOneParticipantInOneStudy = new DDPAuth(ISSUER, CLIENT_GUID, LOGGED_IN_OPERATOR_GUID,
                userPermissions, PREFERRED_LANGUAGE);

        UserPermissions revokedClientPermissions = new UserPermissions(LOGGED_IN_OPERATOR_GUID, false,
                true, aclForClientStudy, aclForOtherParticipants, new ArrayList<>());
        userWithRevokedClient = new DDPAuth(ISSUER, CLIENT_GUID, LOGGED_IN_OPERATOR_GUID,
                revokedClientPermissions, PREFERRED_LANGUAGE);

        UserPermissions lockedAccountPermissions = new UserPermissions(LOGGED_IN_OPERATOR_GUID,
                false, true, aclForClientStudy, aclForOtherParticipants, new ArrayList<>());
        userWithLockedAccount = new DDPAuth(ISSUER, CLIENT_GUID, LOGGED_IN_OPERATOR_GUID,
                lockedAccountPermissions, PREFERRED_LANGUAGE);

    }

    /**
     * Verifies that when empty values are passed to the constructor, no access is allowed.
     */
    @Test
    public void testNoAccessForMissingFields() {
        DDPAuth ddpAuth = new DDPAuth(null, null, null, null, null);
        Assert.assertFalse(ddpAuth.isActive());
        Assert.assertFalse(ddpAuth.canAccessGovernedUsers("123"));
        Assert.assertFalse(ddpAuth.canAccessUserProfile(null, "456"));
        Assert.assertFalse(ddpAuth.canAccessStudyDataForUser("789", "888"));
        Assert.assertFalse(ddpAuth.hasAdminAccessToStudy("012"));
    }

    private void assertNoAccess(DDPAuth ddpAuth) {
        Assert.assertFalse(ddpAuth.isActive());
        Assert.assertFalse(ddpAuth.canAccessStudyDataForUser(OTHER_USER_GUID, STUDY_WITH_ACCESS));
        Assert.assertFalse(ddpAuth.canAccessUserProfile(null, OTHER_USER_GUID));
        Assert.assertFalse(ddpAuth.canAccessUserProfile(null, LOGGED_IN_OPERATOR_GUID));
        Assert.assertFalse(ddpAuth.canAccessGovernedUsers(LOGGED_IN_OPERATOR_GUID));
        Assert.assertFalse(ddpAuth.canAccessGovernedUsers(OTHER_USER_GUID));
        Assert.assertFalse(ddpAuth.canAccessStudyDataForUser(OTHER_USER_GUID, "study that you don't have access to"));
        Assert.assertFalse(ddpAuth.hasAdminAccessToStudy(LOGGED_IN_OPERATOR_GUID));
    }

    @Test
    public void testNoAccessWhenAccountLocked() {
        assertNoAccess(userWithLockedAccount);
    }


    @Test
    public void testNoAccessWhenClientRevoked() {
        assertNoAccess(userWithRevokedClient);
    }

    /**
     * Verifies that when this user has another user in their list of governed participants for a study, they get access
     * to that study for the other user.
     */
    @Test
    public void testAccessForStudyWithAccessForOtherUser() {
        Assert.assertTrue(userWithAccessToOneParticipantInOneStudy.isActive());
        Assert.assertTrue(userWithAccessToOneParticipantInOneStudy
                .canAccessStudyDataForUser(OTHER_USER_GUID, STUDY_WITH_ACCESS));
    }

    @Test
    public void testCanAccessOwnProfile() {
        Assert.assertTrue(userWithAccessToOneParticipantInOneStudy.canAccessGovernedUsers(LOGGED_IN_OPERATOR_GUID));
    }

    @Test
    public void testCanAccessOwnGovernedParticipants() {
        Assert.assertTrue(userWithAccessToOneParticipantInOneStudy.canAccessGovernedUsers(LOGGED_IN_OPERATOR_GUID));
    }

    @Test
    public void testCanAccessOwnAnyStudyForSelf() {
        Assert.assertTrue(userWithAccessToOneParticipantInOneStudy
                .canAccessStudyDataForUser(LOGGED_IN_OPERATOR_GUID, STUDY_WITH_ACCESS));
    }

    @Test
    public void testCannotAccessStudyNotPermittedForUserClient() {
        Assert.assertFalse(userWithAccessToOneParticipantInOneStudy
                .canAccessStudyDataForUser(OTHER_USER_GUID, "another-study"));
    }

    /**
     * Verifies that when this user cannot access a study for a user that's not in their list of governed participants
     * or for the user that's in their list of governed participants for a different study.
     */
    @Test
    public void testCannotAccessStudyAccessForOtherUser() {
        Assert.assertFalse(userWithAccessToOneParticipantInOneStudy
                .canAccessStudyDataForUser(OTHER_USER_GUID, "study that you don't have access to"));
        Assert.assertFalse(userWithAccessToOneParticipantInOneStudy
                .canAccessStudyDataForUser("some other user", STUDY_WITH_ACCESS));
    }

    /**
     * Verifies that when this user governs another user, they have access to that user's profile.
     */
    @Test
    public void testCanAccessProfileForOtherUser() {
        Assert.assertTrue(userWithAccessToOneParticipantInOneStudy.canAccessUserProfile(null, OTHER_USER_GUID));
        Assert.assertTrue(userWithAccessToOneParticipantInOneStudy.canAccessUserProfile(null, LOGGED_IN_OPERATOR_GUID));
    }

    /**
     * Verifies that when this user does not govern another user, they do not have access to the other user's profile.
     */
    @Test
    public void testCannotAccessProfileForOtherUser() {
        Assert.assertFalse(userWithAccessToOneParticipantInOneStudy.canAccessUserProfile(null, "someoneelse"));
    }

    /**
     * Verifies that this user cannot under any circumstances access the governed users for another user, even if this
     * user governs the other user.
     */
    @Test
    public void testCannotAccessGovernedParticipantsForOtherUser() {
        Assert.assertFalse(userWithAccessToOneParticipantInOneStudy.canAccessGovernedUsers(OTHER_USER_GUID));
        Assert.assertFalse(userWithAccessToOneParticipantInOneStudy.canAccessGovernedUsers("random other user guid"));
    }

    @Test
    public void testAdmin_accessStudyData() {
        var permissions = new UserPermissions("operator", false, false, List.of("study"), List.of(), List.of());
        var auth = new DDPAuth(ISSUER, "client", "operator", permissions, "en");
        Assert.assertFalse(
                "should not allow access because operator is not admin or proxy",
                auth.canAccessStudyDataForUser("user", "study"));

        permissions = new UserPermissions("operator", false, false, List.of("study"), List.of(), List.of("study"));
        auth = new DDPAuth(ISSUER, "client", "operator", permissions, "en");
        Assert.assertTrue(
                "should allow access because operator is admin for study",
                auth.canAccessStudyDataForUser("user", "study"));
    }

    @Test
    public void testAdmin_accessProfileData() {
        Handle mockHandle = mock(Handle.class);
        JdbiUserStudyEnrollment mockEnroll = mock(JdbiUserStudyEnrollment.class);
        doReturn(mockEnroll).when(mockHandle).attach(JdbiUserStudyEnrollment.class);
        doReturn(List.of(new EnrollmentStatusDto(1L, 1L, "user", 1L, "study", EnrollmentStatusType.ENROLLED, 1L, null)))
                .when(mockEnroll).getAllLatestEnrollmentsForUser(any());

        var permissions = new UserPermissions("operator", false, false, List.of("study"), List.of(), List.of());
        var auth = new DDPAuth(ISSUER, "client", "operator", permissions, "en");
        Assert.assertFalse(
                "should not allow access because operator is not admin of study",
                auth.canAccessUserProfile(mockHandle, "user"));

        permissions = new UserPermissions("operator", false, false, List.of("study"), List.of(), List.of("study"));
        auth = new DDPAuth(ISSUER, "client", "operator", permissions, "en");
        Assert.assertTrue(
                "should allow access because operator is admin for study",
                auth.canAccessUserProfile(mockHandle, "user"));
    }
}
