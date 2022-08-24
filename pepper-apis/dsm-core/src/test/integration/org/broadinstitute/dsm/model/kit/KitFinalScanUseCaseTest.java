package org.broadinstitute.dsm.model.kit;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.TestInstanceCreator;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.junit.Test;

public class KitFinalScanUseCaseTest extends KitBaseUseCaseTest {

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
        List<ScanError> scanErrors = kitFinalScanUseCase.get();
        assertEquals(0, scanErrors.size());
        assertEquals("kitLabel", kitDao.getKit(kitId.longValue()).map(KitRequestShipping::getKitLabel).orElse(StringUtils.EMPTY));
        assertEquals("kitLabel2", kitDao.getKit(kitId2.longValue()).map(KitRequestShipping::getKitLabel).orElse(StringUtils.EMPTY));
    }

    @Test
    public void processIsBloodKitHasNoTracking() {
        KitRequestShipping kitRequestShipping = getKitRequestShipping("ddpLabel", "TEST3");

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


        ScanError scanError =
                new ScanError("ddpLabel", "Kit with DSM Label " + "ddpLabel" + " does not have a Tracking Label");
        List<ScanPayload> scanPayloads = List.of(
                new SentAndFinalScanPayload("ddpLabel", "kitLabel")
        );
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(TestInstanceCreator.TEST_INSTANCE).orElseThrow();
        KitPayload kitPayload = new KitPayload(scanPayloads, 94, ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, kitDao);
        List<ScanError> scanErrors = kitFinalScanUseCase.get();
        assertEquals(1, scanErrors.size());
        assertEquals(scanError, scanErrors.get(0));
    }

    @Test
    public void barcodeLessThan14Digits() {
        List<ScanPayload> scanPayloads = Arrays.asList(
                new SentAndFinalScanPayload("ddpLabel", "<14"),
                new SentAndFinalScanPayload("ddpLabel2", "<14")
        );
        KitPayload kitPayload = new KitPayload(scanPayloads, 94, null);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, null);
        List<ScanError> scanErrors = kitFinalScanUseCase.get();
        assertEquals(2, scanErrors.size());
        assertEquals(new ScanError("ddpLabel", "Barcode contains less than 14 digits, "
                + "You can manually enter any missing digits above."), scanErrors.get(0));
        assertEquals(new ScanError("ddpLabel2", "Barcode contains less than 14 digits, "
                + "You can manually enter any missing digits above."), scanErrors.get(1));
    }

}
