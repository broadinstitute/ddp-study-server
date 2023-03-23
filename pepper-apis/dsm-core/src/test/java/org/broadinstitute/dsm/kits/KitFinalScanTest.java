package org.broadinstitute.dsm.kits;


import org.broadinstitute.dsm.model.kit.ScanError;
import org.junit.Assert;
import org.junit.Test;

public class KitFinalScanTest {

    @Test
    public void testIsScanErrorOnlyBspParticipantId(){
        String kit = "TEST_KIT_LABEL";
        String error = null;
        String shortId = "TEST_SHORT_ID";

        ScanError notRealScanError = new ScanError(kit, error, shortId);
        Assert.assertTrue(notRealScanError.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));

        error = "This is real error";
        ScanError realScanError = new ScanError(kit, error, shortId);
        Assert.assertFalse(realScanError.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));

        shortId = null;
        ScanError realScanError2 = new ScanError(kit, error, shortId);
        Assert.assertFalse(realScanError2.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }

}