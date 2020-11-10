package org.broadinstitute.ddp.environment;

import org.junit.Assert;
import org.junit.Test;

public class HostUtilTest {

    // should be set in pom.xml
    public static final String INSTANCE_ID = "test-hostname";

    @Test
    public void testHostNameForGAE() {
        Assert.assertEquals(INSTANCE_ID, HostUtil.getHostName());
    }

}
