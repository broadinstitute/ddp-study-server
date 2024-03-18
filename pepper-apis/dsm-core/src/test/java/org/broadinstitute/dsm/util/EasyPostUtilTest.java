package org.broadinstitute.dsm.util;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
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
}
