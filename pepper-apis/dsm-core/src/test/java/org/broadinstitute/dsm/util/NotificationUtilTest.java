package org.broadinstitute.dsm.util;

import static org.junit.Assert.fail;

import com.google.gson.JsonElement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;

public class NotificationUtilTest {

    @Test
    public void testInitialization() {
        Config cfg = ConfigFactory.parseResources("NotificationUtilTest.conf");
        try {
            var notificationUtil = new NotificationUtil(cfg);
            notificationUtil.initialize(cfg);
            JsonElement ptpReminder = notificationUtil.getPortalReminderNotifications("PARTICIPANT_REMINDER");
            Assert.assertEquals("templateId",
                    ptpReminder.getAsJsonArray().get(0).getAsJsonObject().get("sendGridTemplate").getAsString());
            String notification = notificationUtil.getTemplate("PARTICIPANT_ASSIGNED");
            Assert.assertEquals("templateId2", notification);
        } catch (Exception e) {
            fail("Could not start notification util due to " + e.getMessage());
        }
    }
}
