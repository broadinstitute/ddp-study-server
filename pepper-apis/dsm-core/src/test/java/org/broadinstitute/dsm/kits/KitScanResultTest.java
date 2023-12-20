package org.broadinstitute.dsm.kits;

import org.broadinstitute.dsm.model.kit.ScanResult;
import org.junit.Assert;
import org.junit.Test;

public class KitScanResultTest {

    @Test
    public void testShortIdIsNotErrror(){
        String kit = "TEST_KIT_LABEL";
        String error = null;
        String shortId = "TEST_SHORT_ID";

        ScanResult notRealScanResult = new ScanResult(kit, error, shortId);
        Assert.assertTrue(notRealScanResult.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }

    @Test
    public void testScanErrorAndShortINotNull(){
        String kit = "TEST_KIT_LABEL";
        String error = "This is real error";
        String shortId = "TEST_SHORT_ID";

        ScanResult realScanResult = new ScanResult(kit, error, shortId);
        Assert.assertFalse(realScanResult.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }

    @Test
    public void testScanErrorNotNullShortIdNull(){
        String kit = "TEST_KIT_LABEL";
        String error = "This is real error";
        String shortId = null;

        ScanResult realScanResult2 = new ScanResult(kit, error, shortId);
        Assert.assertFalse(realScanResult2.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }

    @Test
    public void testScanErrorNullShortIdBroken(){
        String kit = "TEST_KIT_LABEL";
        String error = null;
        String shortId = "TEST_SHORT_ID";

        ScanResult realScanResult2 = new ScanResult(kit, error, shortId);
        Assert.assertFalse(realScanResult2.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID_BROKEN"));
    }

    @Test
    public void testScanErrorNullShortIdNull(){
        String kit = "TEST_KIT_LABEL";
        String error = null;
        String shortId = null;

        ScanResult realScanResult2 = new ScanResult(kit, error, shortId);
        Assert.assertFalse(realScanResult2.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }

    @Test
    public void testScanErrorNullKitNull(){
        String kit = null;
        String error = null;
        String shortId = "TEST_SHORT_ID";

        ScanResult realScanResult2 = new ScanResult(kit, error, shortId);
        Assert.assertTrue(realScanResult2.isScanErrorOnlyBspParticipantId("TEST_SHORT_ID"));
    }
}
