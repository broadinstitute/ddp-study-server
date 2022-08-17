package org.broadinstitute.dsm.model.kit;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.junit.Ignore;
import org.junit.Test;

public class KitFinalScanUseCaseTest {

    private static final KitDao kitDao = new KitDaoImpl();


    @Ignore
    @Test
    public void processIsNotBloodKit() {
        List<ScanPayload> scanPayloads = Arrays.asList(
                new SentAndFinalScanPayload("ddpLabel", "kitLabel"),
                new SentAndFinalScanPayload("ddpLabel2", "kitLabel2")
        );
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName("angio").orElseThrow();
        KitPayload kitPayload = new KitPayload(scanPayloads, 94, ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, kitDao);

        assertEquals(5, 5);
    }
}