package org.broadinstitute.lddp.util;

import org.broadinstitute.dsm.util.EasyPostUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeliveryAddressTest {

    @BeforeClass
    public static void setUp() {
        EasyPostUtil.initializeEasyPostFromTypeSafeConfig();
    }

    @Test
    public void testNullAddress2() {
        var address = new DeliveryAddress("75 Ames St.", null, "Cambridge", "MA", "02141", "US");
        Assert.assertFalse(address.isEmpty());
        Assert.assertTrue(address.validate());
    }
}
