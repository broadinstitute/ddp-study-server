package org.broadinstitute.dsm.db.dao;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dao.kit.KitTypeDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.model.kit.ScanError;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;

@Slf4j
public class KitDaoImplTest extends DbTxnBaseTest {

    private static KitDaoImpl kitDao = new KitDaoImpl();

    private static Long kitId;

    private static Integer participantId;

    private static Long kitRequestId;

    private static UserAdminTestUtil userAdminTestUtil = new UserAdminTestUtil();

    private static ParticipantDao participantDao = new ParticipantDao();

    private static KitTypeDao kitTypeDao = new KitTypeImpl();

    private static final String KIT_NAME = "KITDAOTEST";

    private static final String KIT_TYPE_NAME = "KITDAOTEST_TYPE";

    private static final String PARTICIPANT_ID = "KITDAOTEST123";

    private static Integer ddpInstanceId;

    private static Integer kitTypeId;

    private static final String TRACKING_RETURN_ID = KIT_NAME + "TRACKINGID";

    private static Integer userId;

    @BeforeClass
    public static void insertTestKit() {
        userAdminTestUtil.createRealmAndStudyGroup(KIT_NAME, KIT_NAME, KIT_NAME, KIT_NAME);
        userId = userAdminTestUtil.setStudyAdminAndRoles("kit_tester@datadonationplaform.org", USER_ADMIN_ROLE,
                List.of(USER_ADMIN_ROLE));
        KitTypeDto kitTypeDto = KitTypeDto.builder()
                .withRequiresInsertInKitTracking(false)
                .withRequiredRole(0) // todo arz get real admin role
                .withManualSentTrack(false)
                .withNoReturn(false)
                .withKitTypeName(KIT_TYPE_NAME)  // todo arz is there a sample type column in the database?
                .build();
        kitTypeId = kitTypeDao.create(kitTypeDto);
        ddpInstanceId = userAdminTestUtil.getDdpInstanceId();
        ParticipantDto participantDto =
                new ParticipantDto.Builder(ddpInstanceId, System.currentTimeMillis())
                        .withDdpParticipantId(PARTICIPANT_ID)
                        .withLastVersion(0)
                        .withLastVersionDate("")
                        .withChangedBy("SYSTEM")
                        .build();
        participantId = new ParticipantDao().create(participantDto);

        KitRequestShipping kitReq = new KitRequestShipping(ddpInstanceId);
        kitReq.setDdpParticipantId(participantDto.getDdpParticipantId().orElse(""));
        kitReq.setParticipantId(PARTICIPANT_ID);
        kitReq.setKitLabel(KIT_NAME);
        kitReq.setCreatedDate(System.currentTimeMillis());
        kitReq.setDdpKitRequestId(KIT_NAME);
        kitReq.setKitTypeId(Long.toString(kitTypeId));
        kitRequestId = kitDao.insertKitRequest(kitReq);
        kitReq.setDsmKitRequestId(Long.valueOf(kitRequestId));
        kitId = kitDao.insertKit(kitReq);
    }

    @AfterClass
    public static void deleteTestKit() {
        if (participantId != null) {
            participantDao.delete(participantId);
        }
        kitDao.deleteKitTrackingByKitLabel(KIT_NAME);
        if (kitId != null) {
            kitDao.deleteKit(kitId);
        }
        if (kitRequestId != null) {
            kitDao.deleteKitRequest(kitRequestId);
        }
        if (kitTypeId != null) {
            kitTypeDao.delete(kitTypeId);
        }
        userAdminTestUtil.deleteGeneratedData();
    }

    private void insertKitTrackingAndRunAssertions(boolean shouldTrackingRowExist) {
        Assert.assertEquals(shouldTrackingRowExist, kitDao.hasTrackingScan(KIT_NAME));
        Optional<ScanError> scanError = kitDao.insertKitTrackingIfNotExists(KIT_NAME, TRACKING_RETURN_ID, userId);
        Assert.assertTrue(scanError.isEmpty());
        Assert.assertTrue(kitDao.hasTrackingScan(KIT_NAME));
    }

    @After
    public void deleteKitTracking() {
        kitDao.deleteKitTrackingByKitLabel(KIT_NAME);
    }

    @Test
    public void testInsertNewKitTrackingForKitThatHasNoTracking() {
        insertKitTrackingAndRunAssertions(false);
    }

    @Test
    public void testInsertNewKitTrackingForKitThatAlreadyHasTracking() {
        insertKitTrackingAndRunAssertions(false);
        insertKitTrackingAndRunAssertions(true);
    }
}
