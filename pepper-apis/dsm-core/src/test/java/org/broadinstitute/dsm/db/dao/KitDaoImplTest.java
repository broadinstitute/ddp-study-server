package org.broadinstitute.dsm.db.dao;

import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dao.kit.KitTypeDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class KitDaoImplTest extends DbTxnBaseTest {

    private static final KitDaoImpl kitDao = new KitDaoImpl();

    private static final UserAdminTestUtil userAdminTestUtil = new UserAdminTestUtil();

    private static final ParticipantDao participantDao = new ParticipantDao();

    private static final KitTypeDao kitTypeDao = new KitTypeImpl();

    private static final String KIT_NAME = "KITDAOTEST";

    private static final String KIT_TYPE_NAME = "KITDAOTEST_TYPE";

    private static final String PARTICIPANT_ID = "KITDAOTEST123";

    private static final String TRACKING_RETURN_ID = KIT_NAME + "TRACKINGID";

    private static Integer kitId;

    private static Integer participantId;

    private static Integer kitRequestId;

    private static Integer kitTypeId;

    private static Integer userId;

    private static KitRequestShipping kitReq;

    @BeforeClass
    public static void insertTestKit() {
        userAdminTestUtil.createRealmAndStudyGroup(KIT_NAME, KIT_NAME, KIT_NAME, KIT_NAME, KIT_NAME);
        userId = userAdminTestUtil.setStudyAdminAndRoles("kit_tester@datadonationplaform.org", USER_ADMIN_ROLE,
                List.of(USER_ADMIN_ROLE));
        KitTypeDto kitTypeDto = KitTypeDto.builder()
                .withRequiresInsertInKitTracking(false)
                .withRequiredRole(userAdminTestUtil.getRoleId(USER_ADMIN_ROLE))
                .withManualSentTrack(false)
                .withNoReturn(false)
                .withKitTypeName(KIT_TYPE_NAME)
                .build();
        kitTypeId = kitTypeDao.create(kitTypeDto);
        int ddpInstanceId = userAdminTestUtil.getDdpInstanceId();
        ParticipantDto participantDto =
                new ParticipantDto.Builder(ddpInstanceId, System.currentTimeMillis())
                        .withDdpParticipantId(PARTICIPANT_ID)
                        .withLastVersion(0)
                        .withLastVersionDate("")
                        .withChangedBy("SYSTEM")
                        .build();
        participantId = new ParticipantDao().create(participantDto);

        kitReq = new KitRequestShipping(ddpInstanceId);
        kitReq.setDdpParticipantId(participantDto.getDdpParticipantId().orElse(""));
        kitReq.setParticipantId(PARTICIPANT_ID);
        kitReq.setKitLabel(KIT_NAME);
        kitReq.setDdpLabel(KIT_NAME);
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

    private void insertKitTrackingAndRunAssertions(boolean shouldTrackingRowExistAndScanErrorShouldExist) {
        Assert.assertEquals(shouldTrackingRowExistAndScanErrorShouldExist, kitDao.hasTrackingScan(KIT_NAME));
        Optional<ScanResult> scanError = kitDao.insertKitTrackingIfNotExists(KIT_NAME, TRACKING_RETURN_ID, userId);
        Assert.assertEquals(shouldTrackingRowExistAndScanErrorShouldExist, !scanError.isEmpty());
        if (shouldTrackingRowExistAndScanErrorShouldExist) {
            Assert.assertTrue(scanError.get().hasError());
        }
        Assert.assertTrue(kitDao.hasTrackingScan(KIT_NAME));
    }

    @After
    public void deleteKitTracking() {
        kitDao.deleteKitTrackingByKitLabel(KIT_NAME);
    }

    @Test
    public void testInsertNewKitTrackingForKitThatHasNoTracking() {
        // new kit, no existing data or scan error expecting
        insertKitTrackingAndRunAssertions(false);
    }

    @Test
    public void testInsertNewKitTrackingForKitThatAlreadyHasTracking() {
        // first time around, no existing data should exist and no scan error should be returned
        insertKitTrackingAndRunAssertions(false);
        // attempting the same thing again should result in an existing row and a scan error
        insertKitTrackingAndRunAssertions(true);
    }

    @Test
    public void testKitScanInfoUpdateWithAndWithoutExistingScanInfo() {
        // create a kit
        Optional<ScanResult> kitInsertScanResult = kitDao.insertKitTrackingIfNotExists(KIT_NAME, TRACKING_RETURN_ID,
                userId);
        Assert.assertTrue(kitInsertScanResult.isEmpty());

        // now try to update scan information that is already present
        KitRequestShipping scanUpdate = new KitRequestShipping();
        scanUpdate.setKitLabel(KIT_NAME);
        scanUpdate.setTrackingReturnId(null);
        scanUpdate.setScanDate(null);
        scanUpdate.setDdpLabel(KIT_NAME);
        scanUpdate.setDsmKitId(Long.valueOf(kitId));

        Optional<ScanResult> scanError = kitDao.updateKitScanInfo(scanUpdate, "tester");
        Assert.assertTrue("Updating an existing kit that already has scan information should result in an error.",
                scanError.get().hasError());

        // reset some ddp_kit fields and confirm that you can update the scan fields
        kitDao.updateKitComplete(kitId, false);
        scanUpdate.setKitLabel(KIT_NAME + ".ignore");
        Assert.assertTrue(kitDao.updateKitLabel(scanUpdate).isEmpty());
        scanUpdate.setKitLabel(KIT_NAME);

        Optional<ScanResult> hasScanError = kitDao.updateKitScanInfo(scanUpdate, "tester");
        Assert.assertTrue("Updating an existing kit with no scan information should not result in a scan error.",
                hasScanError.isEmpty());
        kitDao.updateKitLabel(scanUpdate);

        // change the kit label and confirm that there is an error because there's no ddp_kit with the given label
        scanUpdate.setDdpLabel(KIT_NAME + System.currentTimeMillis());
        scanError = kitDao.updateKitScanInfo(scanUpdate, "tester");
        Assert.assertTrue("Updating an existing kit request that has no ddp_kit row should result in a scan error.",
                scanError.get().hasError());
        scanUpdate.setDdpLabel(KIT_NAME);
    }

}
