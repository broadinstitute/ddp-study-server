package org.broadinstitute.dsm.service;

import static org.junit.Assert.assertNotNull;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.util.ConfigManager;
import org.junit.BeforeClass;
import org.junit.Test;

public class SomaticResultUploadServiceTest {

    static SomaticResultUploadService somaticResultUploadSerivce;

    @BeforeClass
    public static void setup() {
        Config loadedConfig = ConfigManager.getInstance().getConfig();
        somaticResultUploadSerivce = SomaticResultUploadService.fromConfig(loadedConfig);
    }

    @Test
    public void test_authorizeValidRequest() {
        assertNotNull(somaticResultUploadSerivce);
    }
}
