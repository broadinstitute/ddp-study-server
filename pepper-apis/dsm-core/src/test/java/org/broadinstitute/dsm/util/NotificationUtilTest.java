package org.broadinstitute.dsm.util;

import static org.junit.Assert.fail;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

public class NotificationUtilTest {

    @Test
    public void testInitialization() {
        Config cfg = ConfigFactory.parseResources("NotificationUtilTest.conf");
        try {
            var notificationUtil = new NotificationUtil(cfg);
            notificationUtil.initialize(cfg);
        } catch (Exception e) {
            fail("Could not start notification util due to " + e.getMessage());
        }
    }
}
