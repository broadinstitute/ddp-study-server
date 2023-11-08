package org.broadinstitute.ddp.cache;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class CacheServiceTest {

    private static final String AARCH64 = "aarch64";

    @Test
    public void testGetInstanceDoesNotThrowNoSuchMethodError() {
        try {
            CacheService.getInstance();
        } catch (NoSuchMethodError e) {
            e.printStackTrace();
            Assert.fail("Transitive dependency error with redisson: " + e.getMessage());
        } catch (NoSuchFieldError e) {
            if ("NETWORK_INTERFACES".equals(e.getMessage())) {
                //noinspection StatementWithEmptyBody
                if (AARCH64.equals(System.getProperty("os.arch"))) {
                    // this is okay because the test is just asserting that we didn't
                    // hit NoSuchMethodError, and the network interfaces issue
                    // is an osx specific problem
                } else {
                    Assert.fail("Could not get CacheService instance: " + e.getMessage());
                }
            }
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
            if (hasConfigFileError) {
                e.printStackTrace();
            }
            Assert.assertTrue(hasConfigFileError);
        }
    }
}
