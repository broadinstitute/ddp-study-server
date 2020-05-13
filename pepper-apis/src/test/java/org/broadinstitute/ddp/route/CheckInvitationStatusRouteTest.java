package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.restassured.mapper.ObjectMapperType;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.json.invitation.CheckInvitationStatusPayload;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CheckInvitationStatusRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;

    private Handle mockHandle;
    private JdbiUmbrellaStudy mockJdbiStudy;
    private JdbiClientUmbrellaStudy mockClientStudy;
    private InvitationDao mockInviteDao;
    private CheckInvitationStatusRoute route;

    @BeforeClass
    public static void setupData() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
        });
    }

    @Before
    public void setupMocks() {
        mockHandle = mock(Handle.class);
        mockJdbiStudy = mock(JdbiUmbrellaStudy.class);
        mockClientStudy = mock(JdbiClientUmbrellaStudy.class);
        mockInviteDao = mock(InvitationDao.class);
        doReturn(mockJdbiStudy).when(mockHandle).attach(JdbiUmbrellaStudy.class);
        doReturn(mockClientStudy).when(mockHandle).attach(JdbiClientUmbrellaStudy.class);
        doReturn(mockInviteDao).when(mockHandle).attach(InvitationDao.class);
        route = new CheckInvitationStatusRoute();
    }

    @Test
    public void testCheckStatus_studyNotFound() {
        doReturn(null).when(mockJdbiStudy).findByStudyGuid(any());
        var payload = new CheckInvitationStatusPayload("foo", "bar", "invite");
        int actual = route.checkStatus(mockHandle, "study", payload);
        assertEquals(HttpStatus.SC_BAD_REQUEST, actual);
    }

    @Test
    public void testCheckStatus_clientNotFound() {
        doReturn(testData.getTestingStudy()).when(mockJdbiStudy).findByStudyGuid(any());
        doReturn(Collections.emptyList()).when(mockClientStudy)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(any(), any());

        var payload = new CheckInvitationStatusPayload("foo", "bar", "invite");
        int actual = route.checkStatus(mockHandle, "study", payload);
        assertEquals(HttpStatus.SC_BAD_REQUEST, actual);
    }

    @Test
    public void testCheckStatus_clientNoAccess() {
        doReturn(testData.getTestingStudy()).when(mockJdbiStudy).findByStudyGuid(any());
        doReturn(List.of("study1", "study2")).when(mockClientStudy)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(any(), any());

        var payload = new CheckInvitationStatusPayload("foo", "bar", "invite");
        int actual = route.checkStatus(mockHandle, "study", payload);
        assertEquals(HttpStatus.SC_BAD_REQUEST, actual);
    }

    @Test
    public void testCheckStatus_badInvitation() {
        doReturn(testData.getTestingStudy()).when(mockJdbiStudy).findByStudyGuid(any());
        doReturn(List.of(testData.getStudyGuid())).when(mockClientStudy)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(any(), any());
        var payload = new CheckInvitationStatusPayload("foo", "bar", "invite");

        doReturn(Optional.empty()).when(mockInviteDao).findByInvitationGuid(anyLong(), any());
        int actual = route.checkStatus(mockHandle, "study", payload);
        assertEquals(HttpStatus.SC_BAD_REQUEST, actual);

        var now = Instant.now();
        var voided = fakeInvitation(now, now, null, null);
        doReturn(Optional.of(voided)).when(mockInviteDao).findByInvitationGuid(anyLong(), any());
        actual = route.checkStatus(mockHandle, "study", payload);
        assertEquals(HttpStatus.SC_BAD_REQUEST, actual);

        var accepted = fakeInvitation(now, null, null, now);
        doReturn(Optional.of(accepted)).when(mockInviteDao).findByInvitationGuid(anyLong(), any());
        actual = route.checkStatus(mockHandle, "study", payload);
        assertEquals(HttpStatus.SC_BAD_REQUEST, actual);
    }

    @Test
    public void testGoodInvitation() {
        InvitationDto invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis()));
        try {
            var payload = new CheckInvitationStatusPayload(
                    testData.getTestingClient().getAuth0Domain(),
                    testData.getAuth0ClientId(),
                    invitation.getInvitationGuid());
            String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.INVITATION_CHECK;
            url = url.replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
            given().body(payload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(200);
        } finally {
            if (invitation != null) {
                TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                        .deleteById(invitation.getInvitationId()));
            }
        }
    }

    private InvitationDto fakeInvitation(Instant created, Instant voided, Instant verified, Instant accepted) {
        Long userId = accepted == null ? null : testData.getUserId();
        return new InvitationDto(1L, "guid", InvitationType.RECRUITMENT, created, voided, verified, accepted,
                testData.getStudyId(), userId, null, null);
    }
}
