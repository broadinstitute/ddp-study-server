package org.broadinstitute.dsm.kits;

import org.broadinstitute.dsm.model.kit.ScanError;
import org.junit.Assert;
import org.junit.Test;

public class KitScanErrorTest {

    @Test
    public void testShortIdIsNotErrror(){
        String kit = "TEST_KIT_LABEL";
        String error = null;
        String shortId = "TEST_SHORT_ID";

        ScanError notRealScanError = new ScanError(kit, error, shortId);
        Assert.assertTrue(notRealScanError.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }

    @Test
    public void testScanErrorAndShortINotNull(){
        String kit = "TEST_KIT_LABEL";
        String error = "This is real error";
        String shortId = "TEST_SHORT_ID";

        ScanError realScanError = new ScanError(kit, error, shortId);
        Assert.assertFalse(realScanError.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }

    @Test
    public void testScanErrorNotNullShortIdNull(){
        String kit = "TEST_KIT_LABEL";
        String error = "This is real error";
        String shortId = null;

        ScanError realScanError2 = new ScanError(kit, error, shortId);
        Assert.assertFalse(realScanError2.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }
}
