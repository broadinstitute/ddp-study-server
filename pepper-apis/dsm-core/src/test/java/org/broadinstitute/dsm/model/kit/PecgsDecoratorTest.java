package org.broadinstitute.dsm.model.kit;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.ScanPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.junit.Test;

public class PecgsDecoratorTest {

    @Test
    public void hasNotPecgsPrefix() {
        List<ScanPayload> scanPayloads = List.of(
                new SentAndFinalScanPayload("ddpLabel", "kitlabelosteo2")
        );
        DDPInstanceDto osteo2 = new DDPInstanceDto.Builder()
                .withInstanceName("osteo2")
                .build();

        KitPayload kitPayload = new KitPayload(scanPayloads, 94, osteo2);
        PecgsDecorator kitFinalScanUseCase = new PecgsDecorator(kitPayload, new KitDaoMock() {
            @Override
            public Boolean isBloodKit(String kitLabel) {
                return true;
            }
        });
        List<ScanError> scanErrors = kitFinalScanUseCase.get();
        assertEquals(new ScanError("ddpLabel", "No \"PECGS\" prefix found. "
                + "PE-CGS project blood kits should have a \"PECGS\" prefix on barcode. "
                + "Please check to see if this is PE-CGS blood kit before proceeding."), scanErrors.get(0));

    }

}