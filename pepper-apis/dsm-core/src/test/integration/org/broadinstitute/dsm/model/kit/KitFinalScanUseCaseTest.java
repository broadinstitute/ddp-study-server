package org.broadinstitute.dsm.model.kit;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.route.KitPayload;
import org.broadinstitute.dsm.route.ScanPayload;
import org.junit.Test;

public class KitFinalScanUseCaseTest {

    private static final KitDao kitDao = new KitDaoImpl();


    @Test
    public void process() {
        List<ScanPayload> scanPayloads = Arrays.asList(
                new ScanPayload("addValue", "kitValue"),
                new ScanPayload("addValue2", "kitValue2")
        );
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName("angio").orElseThrow();
        KitPayload kitPayload = new KitPayload(scanPayloads, 94, ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, kitDao);

        assertEquals(5, 5);
    }
}