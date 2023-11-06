package org.broadinstitute.ddp.cache;

import org.junit.Assert;
import org.junit.Test;

public class CacheServiceTest {

    @Test
    public void testCacheStartupDoesNotThrowNoSuchMethodError() {
        try {
            CacheService.getInstance();
        } catch (NoSuchMethodError e) {
            e.printStackTrace();
            Assert.fail("Transitive dependency error with redisson: " + e.getMessage());
        } catch (Throwable e) {
            boolean hasConfigFileError = false;
            if (e.getCause() != null) {
                if (e.getCause().getMessage() != null) {
                    if (!e.getCause().getMessage().contains("Path for configuration file file")
                            || !e.getCause().getMessage().contains("config file")) {
                        hasConfigFileError = true;
                    }
                }
            }
            e.printStackTrace();
            Assert.assertTrue(hasConfigFileError);
        }
    }
}
