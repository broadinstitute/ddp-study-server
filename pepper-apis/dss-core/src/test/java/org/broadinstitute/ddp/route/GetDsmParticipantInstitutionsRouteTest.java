package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.TestConstants.TEST_INSTITUTION_LEGACY_GUID;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_DAY;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_MONTH;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_YEAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.dsm.Institution;
import org.broadinstitute.ddp.model.dsm.ParticipantInstitution;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetDsmParticipantInstitutionsRouteTest extends DsmRouteTest {
    private static String legacyAltPid = "12345.GUID-GUID-GUID";
    private static String TEST_LEGACY_SHORTID = "12345.LEGACY-SHORTID";

    @BeforeClass
    public static void testActivitySetup() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.addTestConsent(handle, generatedTestData);
            TestDataSetupUtil.answerTestConsent(handle,
                    true,
                    true,
                    true,
                    TEST_USER_PROFILE_BIRTH_DAY,
                    TEST_USER_PROFILE_BIRTH_MONTH,
                    TEST_USER_PROFILE_BIRTH_YEAR,
                    generatedTestData);
            TestDataSetupUtil.addEnrollmentHook(handle, generatedTestData);
        });
    }

    @AfterClass
    public static void testActivityCleanup() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.deleteEnrollmentHook(handle, generatedTestData);
            TestDataSetupUtil.deleteEnrollmentStatus(handle, generatedTestData);
        });
    }

    @Before
    public void testDataSetup() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil
                    .setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.REGISTERED);

        });
    }

    @After
    public void testDataCleanup() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil
                    .deleteEnrollmentStatus(handle, generatedTestData);
        });
    }

    @Test
    public void testHappyPathIncompleteSurvey() throws Exception {
        String requestUrl = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.DSM_PARTICIPANT_INSTITUTIONS
                .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());

        Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();
        ParticipantInstitution[] participants = new Gson().fromJson(EntityUtils.toString(res.returnResponse().getEntity()),
                ParticipantInstitution[].class);

        assertEquals(0, participants.length);
    }

    @Test
    public void testHappyPathLegacyAltPidAndLegacyShortid() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid, "
                    + " legacy_shortid = :legacyShortId where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("legacyShortId", TEST_LEGACY_SHORTID)
                    .bind("guid", userGuid)
                    .execute());
            TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData, false, true);
            TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED);
        });

        try {
            String requestUrl = RouteTestUtil.getTestingBaseUrl()
                    + RouteConstants.API.DSM_PARTICIPANT_INSTITUTIONS
                    .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());

            Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();
            ParticipantInstitution[] participants = new Gson().fromJson(EntityUtils.toString(res.returnResponse().getEntity()),
                    ParticipantInstitution[].class);

            checkStandardFields(participants);
            assertEquals(legacyAltPid, participants[0].getParticipantId());
            assertEquals(TEST_INSTITUTION_LEGACY_GUID, participants[0].getInstitutions().get(0).getInstitutionId());
            assertEquals(TEST_LEGACY_SHORTID, participants[0].getLegacyShortId());
        } finally {
            TransactionWrapper.useTxn(handle -> {
                assertEquals(1, handle.createUpdate("update user set legacy_altpid = null, "
                        + " legacy_shortid = null where guid = :guid")
                        .bind("guid", userGuid)
                        .execute());
                TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
            });
        }
    }

    private void checkStandardFields(ParticipantInstitution[] participants) {
        assertEquals(1, participants.length);
        ParticipantInstitution participant = participants[0];
        assertEquals(generatedTestData.getProfile().getFirstName(), participant.getFirstName());
        assertEquals(generatedTestData.getProfile().getLastName(), participant.getLastName());
        assertEquals(generatedTestData.getTestingUser().getUserHruid(), participant.getUserHruid());
        assertEquals(generatedTestData.getMailAddress().getCountry(), participant.getCountry());
        assertTrue(participant.getSurveyCreatedMillisSinceEpoch() != null);
        assertTrue(participant.getSurveyLastUpdatedMillisSinceEpoch() != null);
        assertTrue(participant.getSurveyFirstCompletedMillisSinceEpoch() != null);
        assertEquals(1, participant.getAddressValid());

        Institution institution = participant.getInstitutions().get(0);
        assertEquals(InstitutionType.INSTITUTION, institution.getInstitutionType());
        assertEquals(generatedTestData.getMedicalProvider().getInstitutionName(), institution.getInstitutionName());
        assertEquals(generatedTestData.getMedicalProvider().getPhysicianName(), institution.getPhysicianName());
        assertEquals(generatedTestData.getMedicalProvider().getCity(), institution.getCity());
        assertEquals(generatedTestData.getMedicalProvider().getState(), institution.getState());

        ParticipantInstitution.Address address = participant.getAddress();

        assertEquals(generatedTestData.getMailAddress().getStreet1(), address.getStreet1());
        assertEquals(generatedTestData.getMailAddress().getStreet2(), address.getStreet2());
        assertEquals(generatedTestData.getMailAddress().getCity(), address.getCity());
        assertEquals(generatedTestData.getMailAddress().getState(), address.getState());
        assertEquals(generatedTestData.getMailAddress().getZip(), address.getZip());
        assertEquals(generatedTestData.getMailAddress().getCountry(), address.getCountry());
        assertEquals(generatedTestData.getMailAddress().getPhone(), address.getPhone());
        assertEquals(false, address.isEmpty());
        assertEquals(true, address.isValid());
    }

    @Test
    public void testHappyPathCompleteSurvey() throws Exception {
        testHappyPathCompleteSurveyWithEnrollmentStatus(EnrollmentStatusType.ENROLLED);
    }

    private void testHappyPathCompleteSurveyWithEnrollmentStatus(EnrollmentStatusType... enrollmentStatuses) throws Exception {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData);

            for (EnrollmentStatusType enrollmentStatusType : enrollmentStatuses) {
                TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, enrollmentStatusType);
            }
        });
        try {
            String requestUrl = RouteTestUtil.getTestingBaseUrl()
                    + RouteConstants.API.DSM_PARTICIPANT_INSTITUTIONS
                    .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());

            Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();
            ParticipantInstitution[] participants = new Gson().fromJson(EntityUtils.toString(res.returnResponse().getEntity()),
                    ParticipantInstitution[].class);

            checkStandardFields(participants);
            assertEquals(generatedTestData.getMedicalProvider().getUserMedicalProviderGuid(),
                    participants[0].getInstitutions().get(0).getInstitutionId());
        } finally {
            TransactionWrapper.useTxn(handle -> {
                TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
                handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(userGuid, studyGuid,
                        EnrollmentStatusType.ENROLLED);
            });
        }
    }

    @Test
    public void testHappyPathCompleteSurveyExitedParticipant() throws Exception {
        testHappyPathCompleteSurveyWithEnrollmentStatus(EnrollmentStatusType.ENROLLED, EnrollmentStatusType.EXITED_AFTER_ENROLLMENT);
    }

    @Test
    public void testHappyPathCompleteSurveyNoInstitutions() throws Exception {
        // Re-enroll the user without answering questions
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil
                    .setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED);
            TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
            generatedTestData.setMedicalProvider(null);
        });


        String requestUrl = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.DSM_PARTICIPANT_INSTITUTIONS
                .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());

        Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();
        ParticipantInstitution[] participants = new Gson().fromJson(EntityUtils.toString(res.returnResponse().getEntity()),
                ParticipantInstitution[].class);

    }
}
