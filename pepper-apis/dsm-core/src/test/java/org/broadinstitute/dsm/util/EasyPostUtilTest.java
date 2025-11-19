package org.broadinstitute.dsm.util;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.Shipment;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.junit.Assert;
import org.junit.Test;

public class EasyPostUtilTest {

    private EasyPostUtil easyPostUtil =  EasyPostUtil.initializeEasyPostFromTypeSafeConfig();

    @Test
    public void testNullAddress2() {
        try {
            Address easypostAddress = easyPostUtil.createAddressWithoutValidation("foo bar", "75 Ames St.", null, "Cambridge", "02142", "MA", "USA",
                    "5555555555");
            Address verifiedAddress = easypostAddress.verify();
            Assert.assertTrue("Address " + easypostAddress.prettyPrint() + " should be valid but it isn't", verifiedAddress.getVerifications().get("delivery").getSuccess());
        } catch (EasyPostException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testRetryShipmentWithLegacyAccount() throws Exception {
        // this shipment was created with the legacy easypost account.  it should be accessible
        // via getShipment's retry with the legacy easypost account.
        EasyPostUtil.setLegacyApiKey(ConfigManager.getInstance().getConfig().getString(ApplicationConfigConstants.EASYPOST_LEGACY_API_KEY));
        Shipment shipment = easyPostUtil.getShipment("shp_428f62fbafe84b33a4b5d9685981390a");
        Assert.assertTrue(shipment != null);
    }
}
