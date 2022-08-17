package org.broadinstitute.dsm.model.kit;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.TestInstanceCreator;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dao.kit.KitTypeDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.KitStatusChangeRoute;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitFinalScanUseCaseTest {

    private static final KitDao kitDao = new KitDaoImpl();
    private static final KitTypeDao kitTypeDao = new KitTypeImpl();
    private static final List<Integer> kitRequestIds = new ArrayList<>();
    private static final List<Integer> kitIds = new ArrayList<>();
    private static final List<Integer> kitTypeIds = new ArrayList<>();
    private static final TestInstanceCreator testInstance = new TestInstanceCreator();

    @BeforeClass
    public static void setUp() {
        testInstance.create();
    }

    @AfterClass
    public static void finish() {
        for (Integer id: kitIds) {
            kitDao.deleteKit(id.longValue());
        }
        for (Integer id: kitRequestIds) {
            kitDao.deleteKitRequest(id.longValue());
        }
        for (Integer id: kitTypeIds) {
            kitTypeDao.delete(id);
        }
        testInstance.delete();
    }

    @Test
    public void processIsNotBloodKit() {

        KitRequestShipping kitRequestShipping = getKitRequestShipping("ddpLabel", "TEST");
        KitRequestShipping kitRequestShipping2 = getKitRequestShipping("ddpLabel2", "TEST2");

        setAndSaveKitRequestId(kitRequestShipping, kitRequestShipping2);

        Integer kitId = kitDao.insertKit(kitRequestShipping);
        Integer kitId2 = kitDao.insertKit(kitRequestShipping2);
        kitIds.add(kitId);
        kitIds.add(kitId2);


        List<ScanPayload> scanPayloads = Arrays.asList(
                new SentAndFinalScanPayload("ddpLabel", "kitLabel"),
                new SentAndFinalScanPayload("ddpLabel2", "kitLabel2")
        );
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(TestInstanceCreator.TEST_INSTANCE).orElseThrow();
        KitPayload kitPayload = new KitPayload(scanPayloads, 94, ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, kitDao);
        List<KitStatusChangeRoute.ScanError> scanErrors = kitFinalScanUseCase.get();
        assertEquals(0, scanErrors.size());
        assertEquals("kitLabel", kitDao.getKit(kitId.longValue()).map(KitRequestShipping::getKitLabel).orElse(StringUtils.EMPTY));
        assertEquals("kitLabel2", kitDao.getKit(kitId2.longValue()).map(KitRequestShipping::getKitLabel).orElse(StringUtils.EMPTY));
    }

    @Test
    public void processIsBloodKitHasNoTracking() {
        KitRequestShipping kitRequestShipping = getKitRequestShipping("ddpLabel", "TEST");

        KitTypeDto kitTypeDto = KitTypeDto.builder()
                .withRequiresInsertInKitTracking(true)
                .withRequiredRole(0)
                .withManualSentTrack(true)
                .withRequiresInsertInKitTracking(true)
                .withNoReturn(true)
                .build();
        int kitTypeId = kitTypeDao.create(kitTypeDto);
        kitTypeIds.add(kitTypeId);

        kitRequestShipping.setKitTypeId(String.valueOf(kitTypeId));
        setAndSaveKitRequestId(kitRequestShipping);


        KitStatusChangeRoute.ScanError scanError =
                new KitStatusChangeRoute.ScanError("ddpLabel", "Kit with DSM Label " + "ddpLabel" + " does not have a Tracking Label");
        List<ScanPayload> scanPayloads = List.of(
                new SentAndFinalScanPayload("ddpLabel", "kitLabel")
        );
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(TestInstanceCreator.TEST_INSTANCE).orElseThrow();
        KitPayload kitPayload = new KitPayload(scanPayloads, 94, ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, kitDao);
        List<KitStatusChangeRoute.ScanError> scanErrors = kitFinalScanUseCase.get();
        assertEquals(1, scanErrors.size());
        assertEquals(scanError, scanErrors.get(0));
    }

    private void setAndSaveKitRequestId(KitRequestShipping... kitRequestShippings) {
        for (KitRequestShipping kitRequestShipping: kitRequestShippings) {
            Integer insertedKitRequestId = kitDao.insertKitRequest(kitRequestShipping);
            kitRequestIds.add(insertedKitRequestId);
            kitRequestShipping.setDsmKitRequestId(insertedKitRequestId.longValue());
        }
    }

    private KitRequestShipping getKitRequestShipping(String ddpLabel, String ddpKitRequestId) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setDdpLabel(ddpLabel);
        kitRequestShipping.setDdpKitRequestId(ddpKitRequestId);
        kitRequestShipping.setKitTypeId("1");
        kitRequestShipping.setDdpInstanceId(testInstance.getDdpInstanceId().longValue());
        kitRequestShipping.setCreatedDate(System.currentTimeMillis());
        return kitRequestShipping;
    }
}
