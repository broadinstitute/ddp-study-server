package org.broadinstitute.dsm.model.kit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Objects;

import org.broadinstitute.dsm.TestInstanceCreator;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.junit.Test;

public class KitReceivedUseCaseTest extends KitBaseUseCaseTest {

    @Test
    public void process() {

        KitRequestShipping kitRequestShipping = getKitRequestShipping("ddpLabel", "TEST");

        setAndSaveKitRequestId(kitRequestShipping);
        kitRequestShipping.setKitLabel("kitLabel");
        Integer kitId = kitDao.insertKit(kitRequestShipping);
        kitIds.add(kitId);

        List<ScanPayload> scanPayloads = List.of(
                new SentAndFinalScanPayload("ddpLabel", "kitLabel")
        );
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(TestInstanceCreator.TEST_INSTANCE).orElseThrow();
        KitPayload kitPayload = new KitPayload(scanPayloads, USER_ID, ddpInstanceDto);
        KitReceivedUseCase kitReceivedUseCase = new KitReceivedUseCase(kitPayload, kitDao, null);
        List<ScanError> scanErrors = kitReceivedUseCase.get();
        assertEquals(0, scanErrors.size());
        assertTrue(Objects.nonNull(kitDao.getKit(kitId.longValue()).map(KitRequestShipping::getReceiveDate).orElse(null)));
    }
}
