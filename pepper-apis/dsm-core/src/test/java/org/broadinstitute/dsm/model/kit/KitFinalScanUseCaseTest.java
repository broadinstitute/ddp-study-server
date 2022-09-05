package org.broadinstitute.dsm.model.kit;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.junit.Test;

public class KitFinalScanUseCaseTest {
    @Test
    public void barcodeLessThan14Digits() {
        List<ScanPayload> scanPayloads = Arrays.asList(
                new SentAndFinalScanPayload("ddpLabel", "<14"),
                new SentAndFinalScanPayload("ddpLabel2", "<14")
        );
        KitPayload kitPayload = new KitPayload(scanPayloads, 94, null);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, new KitDaoMock());
        List<ScanError> scanErrors = kitFinalScanUseCase.get();
        assertEquals(2, scanErrors.size());
        assertEquals(new ScanError("ddpLabel", "Barcode contains less than 14 digits, "
                + "You can manually enter any missing digits above."), scanErrors.get(0));
        assertEquals(new ScanError("ddpLabel2", "Barcode contains less than 14 digits, "
                + "You can manually enter any missing digits above."), scanErrors.get(1));
    }
}
