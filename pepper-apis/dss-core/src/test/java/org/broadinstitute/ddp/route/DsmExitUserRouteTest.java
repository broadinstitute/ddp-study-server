package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.http.HttpResponse;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DsmExitUserRouteTest extends DsmRouteTest {

    private static final Logger LOG = LoggerFactory.getLogger(DsmExitUserRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData generatedTestData;
    private static String url;
    private static long userStudyEnrollmentId;
    private static ActivityInstanceDto testActivityInstance;
    private static Map<String, String> urlReplacements;
    private static String legacyAltPid = "12345.GUID-GUID-GUID";

    @BeforeClass
    public static void insertTestData() {
        url = RouteTestUtil.getTestingBaseUrl() + API.DSM_TERMINATE_USER;
        TransactionWrapper.useTxn(
                handle -> {
                    generatedTestData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    FormActivityDef formActivity = TestDataSetupUtil.generateTestFormActivityForUser(
                            handle, generatedTestData.getUserGuid(), generatedTestData.getStudyGuid()
                    );
                    testActivityInstance = TestDataSetupUtil.generateTestFormActivityInstanceForUser(
                            handle, formActivity.getActivityId(), generatedTestData.getUserGuid()
                    );
                    // Fold in an Altpid!
                    assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                            .bind("legacyAltPid", legacyAltPid)
                            .bind("guid", userGuid)
                            .execute());
                }
        );
    }

    @AfterClass
    public static void resetTestData() {
        TransactionWrapper.useTxn(handle -> assertEquals(1, handle.createUpdate("update user set legacy_altpid = null where guid = :guid")
                .bind("guid", userGuid)
                .execute()));
    }

    @Before
    public void setupTestData() {
        TransactionWrapper.useTxn(
                handle -> userStudyEnrollmentId = handle.attach(JdbiUserStudyEnrollment.class)
                        .changeUserStudyEnrollmentStatus(
                                generatedTestData.getUserGuid(),
                                generatedTestData.getStudyGuid(),
                                EnrollmentStatusType.REGISTERED
                        ));
        urlReplacements = new HashMap<>();
        urlReplacements.put(PathParam.STUDY_GUID, generatedTestData.getStudyGuid());
        urlReplacements.put(PathParam.USER_GUID, generatedTestData.getUserGuid());
    }

    @After
    public void removeTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiUserStudyEnrollment.class).deleteById(userStudyEnrollmentId);
                });
    }

    /**
     * Verifies that the user exited the study:
     * 1) Its enrollment status is set to "EXITED_BEFORE_ENROLLMENT"
     * 2) All of his/her activity instances are made read-only
     */
    @Test
    public void testTerminateUserWithLegacyPid_Success() throws Exception {
        urlReplacements.put(PathParam.USER_GUID, legacyAltPid);
        HttpResponse response = RouteTestUtil.sendRequestAndReturnResponse(
                RouteTestUtil.RequestMethod.POST,
                url,
                urlReplacements,
                dsmClientAccessToken,
                null
        );
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Optional<EnrollmentStatusType> status = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).getEnrollmentStatusByUserAndStudyGuids(
                        generatedTestData.getUserGuid(), generatedTestData.getStudyGuid()
                )
        );
        Assert.assertTrue("There is no enrollment status for the user/study", status.isPresent());
        Assert.assertEquals("The user enrollment status is different from what we expect",
                EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT, status.get());
        long id = testActivityInstance.getId();
        Optional<Boolean> isReadonly = TransactionWrapper.withTxn(h -> h.attach(JdbiActivityInstance.class).findIsReadonlyById(id));
        Assert.assertTrue("The activity instance with id " + id + " is not found", isReadonly.isPresent());
        Assert.assertTrue("The activity instance with id " + id + " is expected to be r/o but it isn't", isReadonly.get());
    }

    /**
     * Verifies that the user exited the study:
     * 1) Their enrollment status is set to "EXITED_BEFORE_ENROLLMENT"
     * 2) All of his/her activity instances are made read-only
     */
    @Test
    public void testTerminateUser_Success() throws Exception {
        HttpResponse response = RouteTestUtil.sendRequestAndReturnResponse(
                RouteTestUtil.RequestMethod.POST,
                url,
                urlReplacements,
                dsmClientAccessToken,
                null
        );
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Optional<EnrollmentStatusType> status = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).getEnrollmentStatusByUserAndStudyGuids(
                        generatedTestData.getUserGuid(), generatedTestData.getStudyGuid()
                )
        );
        Assert.assertTrue("There is no enrollment status for the user/study", status.isPresent());
        Assert.assertEquals("The user enrollment status is different from what we expect",
                EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT, status.get());
        long id = testActivityInstance.getId();
        Optional<Boolean> isReadonly = TransactionWrapper.withTxn(h -> h.attach(JdbiActivityInstance.class).findIsReadonlyById(id));
        Assert.assertTrue("The activity instance with id " + id + " is not found", isReadonly.isPresent());
        Assert.assertTrue("The activity instance with id " + id + " is expected to be r/o but it isn't", isReadonly.get());
    }

    /**
     * Verifies that the user exited the study:
     * 1) Given they started enrolled
     * 2) Their enrollment status is set to "EXITED_AFTER_ENROLLMENT"
     * 2) All of his/her activity instances are made read-only
     */
    @Test
    public void testTerminateEnrolledUser_Success() throws Exception {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiUserStudyEnrollment.class)
                            .changeUserStudyEnrollmentStatus(
                                    generatedTestData.getUserGuid(),
                                    generatedTestData.getStudyGuid(),
                                    EnrollmentStatusType.ENROLLED
                            );
                });
        LOG.info("testTerminateUser_Success");
        HttpResponse response = RouteTestUtil.sendRequestAndReturnResponse(
                RouteTestUtil.RequestMethod.POST,
                url,
                urlReplacements,
                dsmClientAccessToken,
                null
        );
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Optional<EnrollmentStatusType> status = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).getEnrollmentStatusByUserAndStudyGuids(
                        generatedTestData.getUserGuid(), generatedTestData.getStudyGuid()
                )
        );
        Assert.assertTrue("There is no enrollment status for the user/study", status.isPresent());
        Assert.assertEquals("The user enrollment status is different from what we expect",
                EnrollmentStatusType.EXITED_AFTER_ENROLLMENT, status.get());
        long id = testActivityInstance.getId();
        Optional<Boolean> isReadonly = TransactionWrapper.withTxn(h -> h.attach(JdbiActivityInstance.class).findIsReadonlyById(id));
        Assert.assertTrue("The activity instance with id " + id + " is not found", isReadonly.isPresent());
        Assert.assertTrue("The activity instance with id " + id + " is expected to be r/o but it isn't", isReadonly.get());
    }

    @Test
    public void testTerminateUser_Failure_StudyNotFound() throws Exception {
        Map<String, String> tempUrlReplacements = new HashMap<>(urlReplacements);
        tempUrlReplacements.put(PathParam.STUDY_GUID, "ABBAABBA00");
        HttpResponse response = RouteTestUtil.sendRequestAndReturnResponse(
                RouteTestUtil.RequestMethod.POST,
                url,
                tempUrlReplacements,
                dsmClientAccessToken,
                null
        );
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testTerminateUser_Failure_UserNotFound() throws Exception {
        Map<String, String> tempUrlReplacements = new HashMap<>(urlReplacements);
        tempUrlReplacements.put(PathParam.USER_GUID, "FFAFFAFA11");
        HttpResponse response = RouteTestUtil.sendRequestAndReturnResponse(
                RouteTestUtil.RequestMethod.POST,
                url,
                tempUrlReplacements,
                dsmClientAccessToken,
                null
        );
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

}
